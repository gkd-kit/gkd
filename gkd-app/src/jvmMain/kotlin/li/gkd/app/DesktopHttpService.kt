package li.gkd.app

import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import li.gkd.app.model.AppInfo
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.RpcError
import li.gkd.app.network.AppLinks
import li.gkd.app.network.ServerInfo
import li.gkd.app.network.configureInspectionServer
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.Constants
import li.gkd.app.util.JsonUtils
import li.gkd.app.util.LogUtils
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import org.jetbrains.compose.resources.getString
import java.net.BindException
import javax.swing.SwingUtilities

/** The production inspection routes, bound only to the desktop host's loopback interface. */
class DesktopHttpService(
    private val store: SimulatorStore,
    private val scope: CoroutineScope,
    private val showToast: (String) -> Unit,
) : AutoCloseable {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            stop()
            return
        }
        if (server != null) return
        start(SettingsRepository.settings.value.httpServerPort)
    }

    fun restartIfRunning() {
        val previous = server ?: return
        previous.stop(0, 1000)
        server = null
        start(SettingsRepository.settings.value.httpServerPort)
        if (server != null) showToast(Res.string.http_service_restart_success.getSync())
    }

    private fun start(port: Int) {
        if (!store.settings.value.services.running(SimulatedService.Http)) update(ServicePhase.Starting)
        lateinit var candidate: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
        val failureHandler = CoroutineExceptionHandler { _, cause ->
            if (cause !is Exception) throw cause
            // Startup failure is handled by start() below; later engine failures release this listener.
            SwingUtilities.invokeLater {
                if (server === candidate) {
                    candidate.stop(0, 1000)
                    server = null
                    failed(port, cause)
                }
            }
        }
        candidate = scope.embeddedServer(CIO, port = port, host = Constants.loopbackHost,
            parentCoroutineContext = failureHandler) {
            configureInspectionServer(
                serverInfo = {
                    ServerInfo(
                        DeviceInfo("Desktop", System.getProperty("os.arch"), "", "Desktop", 0, System.getProperty("os.name")),
                        AppInfo(DesktopProfile.appId, DesktopProfile.appName,
                            DesktopProfile.versionCode.toIntOrNull() ?: 0, DesktopProfile.versionName,
                            false, 0, false, 0),
                    )
                },
                captureSnapshot = { throw RpcError(getString(Res.string.platform_action_unsupported)) },
                execSelector = { throw RpcError(getString(Res.string.platform_action_unsupported)) },
                scriptUrl = AppLinks.ServerScript,
                jsonFormat = JsonUtils.keepingNulls,
            )
        }
        try {
            // CIO start returns only after its connectors have bound, including bind failures.
            candidate.start(wait = false)
        } catch (e: Exception) {
            candidate.stop(0, 1000)
            if (e is CancellationException && e.cause == null) throw e
            failed(port, if (e is CancellationException) e.cause!! else e)
            return
        }
        server = candidate
        update(ServicePhase.Running)
    }

    private fun failed(port: Int, cause: Throwable) {
        update(ServicePhase.Failed, cause.message ?: cause.toString())
        LogUtils.d("HTTP service failed", cause)
        showToast(if (generateSequence(cause) { it.cause }.any { it is BindException })
            Res.string.http_port_occupied.getSync(port.toString())
        else Res.string.http_service_start_failed.getSync(cause.stackTraceToString()))
    }

    private fun stop() {
        val previous = server
        previous?.stop(0, 1000)
        server = null
        update(ServicePhase.Stopped)
        if (previous != null && SettingsRepository.settings.value.autoClearMemorySubs) {
            runBlocking(Dispatchers.IO) { SubscriptionRepository.delete(LOCAL_HTTP_SUBS_ID) }
        }
    }

    private fun update(phase: ServicePhase, failure: String? = null) = store.update {
        it.copy(services = it.services.transition(SimulatedService.Http, phase, failure,
            newAttempt = phase == ServicePhase.Starting || phase == ServicePhase.Stopped))
    }

    override fun close() = stop()
}
