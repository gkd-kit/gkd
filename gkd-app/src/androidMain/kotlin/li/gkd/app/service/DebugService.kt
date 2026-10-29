package li.gkd.app.service

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import li.gkd.app.META
import li.gkd.app.appScope
import li.gkd.app.data.appinfo.PackageAppCatalog.selfAppInfo
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.development.AndroidServerDiagnostics
import li.gkd.app.external.ExternalCalls
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.action.executeManualAction
import li.gkd.app.network.AppLinks
import li.gkd.app.network.NetworkAddresses
import li.gkd.app.network.inspection.ServerInfo
import li.gkd.app.network.inspection.configureInspectionServer
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.JsonUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import li.gkd.app.util.mapState
import li.gkd.db.SubscriptionIds

class DebugService : LifecycleHookService() {
    private var started = false
    private var diagnostics: AndroidServerDiagnostics? = null
    val httpServerPortFlow by lazy {
        settings.mapState(lifecycleScope) { s -> s.httpServerPort }
    }

    init {
        useLogLifecycle()
        useStopServiceReceiver()
        onDestroyed {
            if (started && settings.value.autoClearMemorySubs) {
                appScope.launchLogged(Dispatchers.IO) {
                    SubscriptionRepository.delete(SubscriptionIds.LOCAL_HTTP)
                }
            }
        }
        onDestroyed {
            diagnostics?.close()
            if (started) {
                isRunning.value = false
                ToastUtils.show(
                    Res.string.service_stopped.getSync(
                        Res.string.debug_service_compact_label.getSync(),
                    ),
                )
            }
        }
    }

    private fun startServer() {
        if (!NotificationCatalog.debug(httpServerPortFlow.value).startForeground()) return
        started = true
        isRunning.value = true
        ToastUtils.show(
            Res.string.service_started.getSync(Res.string.debug_service_compact_label.getSync()),
        )
        diagnostics = if (META.debuggable) AndroidServerDiagnostics() else null
        var startedOnce = false
        lifecycleScope.launchLogged(Dispatchers.IO) {
            httpServerPortFlow.collectLatest { port ->
                if (!NetworkAddresses.isPortAvailable(port)) {
                    ToastUtils.show(Res.string.debug_port_occupied.getSync(port))
                    stopSelf()
                    return@collectLatest
                }
                val server = createServer(port, diagnostics)
                try {
                    server.start()
                    diagnostics?.publish(port)
                } catch (e: Exception) {
                    server.stop()
                    diagnostics?.unpublish()
                    ToastUtils.show(
                        Res.string.debug_service_start_failed.getSync(e.stackTraceToString()),
                    )
                    LogUtils.e("调试服务启动失败", e)
                    stopSelf()
                    return@collectLatest
                }
                httpServerFlow.value = server
                val localNetworkIps =
                    try {
                        NetworkAddresses.localIpv4Addresses()
                    } catch (e: Exception) {
                        ToastUtils.show(
                            Res.string.network_host_failed_prefix.getSync() + e.message,
                        )
                        emptyList()
                    }
                localNetworkIpsFlow.value = localNetworkIps
                NotificationCatalog.debug(port, localNetworkIps).startForeground()
                if (startedOnce) {
                    ToastUtils.show(Res.string.debug_service_restart_success.getSync())
                }
                startedOnce = true
                try {
                    awaitCancellation()
                } finally {
                    diagnostics?.unpublish()
                    httpServerFlow.compareAndSet(server, null)
                    server.stop()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!META.debuggable && !ExternalCalls.accepts(intent?.getStringExtra("token"))) {
            ToastUtils.show(Res.string.external_calls_invalid_token.getSync(), forced = true)
            if (!started) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!started) startServer()
        return START_NOT_STICKY
    }

    companion object {
        val httpServerFlow: StateFlow<ServerType?>
            field = MutableStateFlow(null)
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)
        val localNetworkIpsFlow: StateFlow<List<String>>
            field = MutableStateFlow(emptyList())

        fun stop() = IntentUtils.stopService(DebugService::class)

        fun start() =
            IntentUtils.startForegroundService(DebugService::class) {
                putExtra("token", ExternalCalls.token())
            }
    }
}

typealias ServerType = EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>

suspend fun clearDebugSubscription() {
    // Clear abandoned HTTP rules only after the subscription repository has loaded.
    if (!DebugService.isRunning.value && settings.value.autoClearMemorySubs) {
        SubscriptionRepository.delete(SubscriptionIds.LOCAL_HTTP)
    }
}

private fun createServer(port: Int, diagnostics: AndroidServerDiagnostics?) =
    embeddedServer(CIO, port) {
        diagnostics?.configure(this)
        configureInspectionServer(
            serverInfo = {
                ServerInfo(
                    currentDeviceInfo(),
                    selfAppInfo,
                )
            },
            captureSnapshot = { SnapshotCaptureHost.capture() },
            execSelector = { executeManualAction(it) },
            scriptUrl = AppLinks.ServerScript,
            jsonFormat = JsonUtils.keepingNulls,
        )
    }
