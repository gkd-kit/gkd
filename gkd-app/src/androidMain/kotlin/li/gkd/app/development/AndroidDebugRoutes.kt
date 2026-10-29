package li.gkd.app.development

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import li.gkd.app.development.matching.benchmarkAndroidMatching
import li.gkd.app.matching.diagnostics.matchForegroundSnapshot

/** Cached engine observations, not a new Binder query or a claim of an atomic OS snapshot. */
fun Route.androidDebugRoutes() {
    post("/debug/android/benchmark") {
        try {
            call.respond(benchmarkAndroidMatching(call.receive()))
        } catch (error: TimeoutCancellationException) {
            call.respond(
                HttpStatusCode.RequestTimeout,
                mapOf(
                    "error" to
                        "Benchmark deadline exceeded; in-flight Binder cleanup has completed",
                ),
            )
        } catch (error: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (error.message ?: "Invalid benchmark request")),
            )
        } catch (error: IllegalStateException) {
            if (error is CancellationException) throw error
            call.respond(
                HttpStatusCode.Conflict,
                mapOf("error" to (error.message ?: "Benchmark unavailable")),
            )
        }
    }
    get("/debug/android/foreground") {
        call.respond(requireNotNull(matchForegroundSnapshot()))
    }
    get("/debug/capabilities") {
        call.respond(
            listOf(
                "/debug/app",
                "/debug/app/catalog",
                "/debug/readiness",
                "/debug/android/foreground",
                "/debug/sessions",
                "/debug/policy",
                "/debug/engine",
                "/debug/android/benchmark",
            ),
        )
    }
}
