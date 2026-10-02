package li.gkd.app.service

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
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.appScope
import li.gkd.app.data.appinfo.PackageAppCatalog.selfAppInfo
import li.gkd.app.data.currentDeviceInfo
import li.gkd.app.network.AppLinks
import li.gkd.app.network.NetworkAddresses
import li.gkd.app.network.ServerInfo
import li.gkd.app.network.configureInspectionServer
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.resources.Res
import li.gkd.app.resources.http_port_occupied
import li.gkd.app.resources.http_service_compact_label
import li.gkd.app.resources.http_service_restart_success
import li.gkd.app.resources.http_service_start_failed
import li.gkd.app.resources.network_host_failed_prefix
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.JsonUtils
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import li.gkd.app.util.mapState
import li.gkd.db.LOCAL_HTTP_SUBS_ID

class HttpService : LifecycleHookService() {
    val httpServerPortFlow by lazy {
        settings.mapState(lifecycleScope) { s -> s.httpServerPort }
    }

    init {
        useLogLifecycle()
        useServicePresence(
            stateFlow = isRunning,
            name = Res.string.http_service_compact_label.getSync(),
        )
        useStopServiceReceiver()
        onDestroyed {
            if (settings.value.autoClearMemorySubs) {
                appScope.launchLogged(Dispatchers.IO) {
                    SubscriptionRepository.delete(LOCAL_HTTP_SUBS_ID)
                }
            }
        }
        onCreated {
            NotificationCatalog.http(httpServerPortFlow.value).startForeground()
            var startedOnce = false
            lifecycleScope.launchLogged(Dispatchers.IO) {
                httpServerPortFlow.collectLatest { port ->
                    if (!NetworkAddresses.isPortAvailable(port)) {
                        ToastUtils.show(Res.string.http_port_occupied.getSync(port))
                        stopSelf()
                        return@collectLatest
                    }
                    val server = try {
                        createServer(port).apply { start() }
                    } catch (e: Exception) {
                        ToastUtils.show(Res.string.http_service_start_failed.getSync(e.stackTraceToString()))
                        LogUtils.d("HTTP服务启动失败", e)
                        stopSelf()
                        return@collectLatest
                    }
                    httpServerFlow.value = server
                    val localNetworkIps = try {
                        NetworkAddresses.localIpv4Addresses()
                    } catch (e: Exception) {
                        ToastUtils.show(Res.string.network_host_failed_prefix.getSync() + e.message)
                        emptyList()
                    }
                    localNetworkIpsFlow.value = localNetworkIps
                    NotificationCatalog.http(port, localNetworkIps).startForeground()
                    if (startedOnce) {
                        ToastUtils.show(Res.string.http_service_restart_success.getSync())
                    }
                    startedOnce = true
                    try {
                        awaitCancellation()
                    } finally {
                        httpServerFlow.compareAndSet(server, null)
                        server.stop()
                    }
                }
            }
        }
    }

    companion object {
        val httpServerFlow: StateFlow<ServerType?>
            field = MutableStateFlow(null)
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)
        val localNetworkIpsFlow: StateFlow<List<String>>
            field = MutableStateFlow(emptyList())

        fun stop() = IntentUtils.stopService(HttpService::class)
        fun start() = IntentUtils.startForegroundService(HttpService::class)

    }
}

typealias ServerType = EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>

suspend fun clearHttpSubs() {
    // Clear abandoned HTTP rules only after the subscription repository has loaded.
    if (!HttpService.isRunning.value && settings.value.autoClearMemorySubs) {
        SubscriptionRepository.delete(LOCAL_HTTP_SUBS_ID)
    }
}

private fun createServer(port: Int) = embeddedServer(CIO, port) {
    configureInspectionServer(
        serverInfo = {
            ServerInfo(
                currentDeviceInfo(),
                selfAppInfo
            )
        },
        captureSnapshot = { SnapshotCaptureHost.capture() },
        execSelector = { A11yRuntime.execAction(it) },
        scriptUrl = AppLinks.ServerScript, jsonFormat = JsonUtils.keepingNulls,
    )
}
