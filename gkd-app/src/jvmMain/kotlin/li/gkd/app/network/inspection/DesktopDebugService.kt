package li.gkd.app.network.inspection

import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.net.BindException
import javax.swing.SwingUtilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import li.gkd.app.DesktopStorage
import li.gkd.app.META
import li.gkd.app.ServicePhase
import li.gkd.app.SimulatedService
import li.gkd.app.SimulatorStore
import li.gkd.app.development.appDebugRoutes
import li.gkd.app.development.matching.matchBenchmarkRoutes
import li.gkd.app.development.matching.matchDebugRoutes
import li.gkd.app.inspection.InspectionException
import li.gkd.app.inspection.InspectionFailure
import li.gkd.app.logging.LogUtils
import li.gkd.app.model.AppInfo
import li.gkd.app.model.DeviceInfo
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.util.Constants
import li.gkd.app.util.JsonUtils
import li.gkd.db.SubscriptionIds

/** One IPv4 listener for browser inspection, app diagnostics and JVM host controls. */
class DesktopDebugService(
    private val store: SimulatorStore,
    private val scope: CoroutineScope,
    private val showToast: (String) -> Unit,
) : AutoCloseable {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? =
        null

    private var controls: (Route.() -> Unit)? = null
    private val connectionFile
        get() = DesktopStorage.sessionDirectory.resolve("debug-server.json")

    fun bindControls(routes: Route.() -> Unit) {
        controls = routes
        setEnabled(true)
    }

    fun unbindControls() {
        stop()
        controls = null
    }

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
        connectionFile.delete()
        previous.stop(0, 1000)
        server = null
        start(SettingsRepository.settings.value.httpServerPort)
        if (server != null) showToast(Res.string.debug_service_restart_success.getSync())
    }

    private fun start(port: Int) {
        val routes = checkNotNull(controls) { "Desktop controls are not bound" }
        if (!store.settings.value.services.running(SimulatedService.Debug))
            update(ServicePhase.Starting)
        lateinit var candidate:
            EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
        val failureHandler = CoroutineExceptionHandler { _, cause ->
            if (cause !is Exception) throw cause
            // Startup failure is handled by start() below; later engine failures release this
            // listener.
            SwingUtilities.invokeLater {
                if (server === candidate) {
                    candidate.stop(0, 1000)
                    server = null
                    connectionFile.delete()
                    failed(port, cause)
                }
            }
        }
        candidate =
            scope.embeddedServer(
                CIO,
                port = port,
                host = "0.0.0.0",
                parentCoroutineContext = failureHandler,
            ) {
                routing {
                    get("/health") { call.respond(mapOf("status" to "ready", "protocol" to "1")) }
                    appDebugRoutes()
                    matchDebugRoutes()
                    matchBenchmarkRoutes()
                    route("/desktop") { routes() }
                }
                configureInspectionServer(
                    serverInfo = {
                        ServerInfo(
                            DeviceInfo(
                                "Desktop",
                                System.getProperty("os.arch"),
                                "",
                                "Desktop",
                                0,
                                System.getProperty("os.name"),
                            ),
                            AppInfo(
                                META.appId,
                                META.appName,
                                META.versionCode,
                                META.versionName,
                                false,
                                0,
                                false,
                                0,
                            ),
                        )
                    },
                    captureSnapshot = {
                        throw InspectionException(InspectionFailure.PlatformUnsupported)
                    },
                    execSelector = {
                        throw InspectionException(InspectionFailure.PlatformUnsupported)
                    },
                    scriptUrl = AppLinks.ServerScript,
                    jsonFormat = JsonUtils.keepingNulls,
                )
            }
        try {
            // CIO start returns only after its connectors have bound, including bind failures.
            candidate.start(wait = false)
            connectionFile.writeText("""{"port":$port}""")
        } catch (e: Exception) {
            candidate.stop(0, 1000)
            connectionFile.delete()
            if (e is CancellationException && e.cause == null) throw e
            failed(port, if (e is CancellationException) e.cause!! else e)
            return
        }
        server = candidate
        update(ServicePhase.Running)
        println(
            "GKD debug service: http://${Constants.loopbackHost}:$port; connection: ${connectionFile.absolutePath}",
        )
    }

    private fun failed(port: Int, cause: Throwable) {
        update(ServicePhase.Failed, cause.message ?: cause.toString())
        LogUtils.e("Debug service failed", cause)
        showToast(
            if (generateSequence(cause) { it.cause }.any { it is BindException })
                Res.string.debug_port_occupied.getSync(port.toString())
            else Res.string.debug_service_start_failed.getSync(cause.stackTraceToString()),
        )
    }

    private fun stop() {
        val previous = server
        connectionFile.delete()
        previous?.stop(0, 1000)
        server = null
        update(ServicePhase.Stopped)
        if (previous != null && SettingsRepository.settings.value.autoClearMemorySubs) {
            runBlocking(Dispatchers.IO) {
                SubscriptionRepository.delete(SubscriptionIds.LOCAL_HTTP)
            }
        }
    }

    private fun update(phase: ServicePhase, failure: String? = null) = store.update {
        it.copy(
            services =
                it.services.transition(
                    SimulatedService.Debug,
                    phase,
                    failure,
                    newAttempt = phase == ServicePhase.Starting || phase == ServicePhase.Stopped,
                ),
        )
    }

    override fun close() = stop()
}
