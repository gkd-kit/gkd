package li.gkd.app.development

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.security.SecureRandom
import li.gkd.app.development.matching.matchDebugRoutes
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.startDiagnosticCapture
import li.gkd.app.storage.appStorage
import org.json.JSONObject

/** Created only in debuggable builds, once per DebugService lifetime. */
class AndroidServerDiagnostics : AutoCloseable {
    private val sessionLock = Any()
    @Volatile private var stopping = false
    private var ownedSession: String? = null
    private val credentials
        get() = appStorage().privateStore.resolve("app-debug.json")

    private val token =
        ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun configure(application: Application) {
        application.configureDebugAccess(token) { stopping }
        application.routing {
            get("/health") { call.respond(mapOf("status" to "ready")) }
            appDebugRoutes()
            androidDebugRoutes()
            matchDebugRoutes(
                onSessionStarted = ::onSessionStarted,
                startCapture = { request ->
                    startDiagnosticCapture(
                        request.detailed,
                        request.ruleFilter,
                        request.recordQueries,
                        request.recordSystem,
                    )
                },
            )
        }
    }

    private fun onSessionStarted(id: String) {
        val cancel =
            synchronized(sessionLock) {
                if (stopping) true
                else {
                    ownedSession = id
                    false
                }
            }
        // A request can finish creating its session while the service is stopping.
        if (cancel) MatchingRuntime.coordinator.stopDiagnostics(id)
    }

    fun publish(port: Int) =
        synchronized(sessionLock) {
            if (!stopping) {
                credentials.writeText(JSONObject().put("port", port).put("token", token).toString())
            }
        }

    fun unpublish() {
        synchronized(sessionLock) { credentials.delete() }
    }

    override fun close() {
        val session =
            synchronized(sessionLock) {
                stopping = true
                ownedSession.also { ownedSession = null }
            }
        try {
            session?.let { MatchingRuntime.coordinator.stopDiagnostics(it) }
        } finally {
            unpublish()
        }
    }
}
