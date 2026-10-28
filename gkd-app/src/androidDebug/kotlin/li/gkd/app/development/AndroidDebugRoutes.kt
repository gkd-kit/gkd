package li.gkd.app.development

import li.gkd.app.matching.diagnostics.matchForegroundSnapshot
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Cached engine observations, not a new Binder query or a claim of an atomic OS snapshot. */
fun Route.androidDebugRoutes() {
    get("/debug/android/foreground") {
        call.respond(requireNotNull(matchForegroundSnapshot()))
    }
    get("/debug/capabilities") {
        call.respond(listOf(
            "/debug/app", "/debug/app/catalog", "/debug/readiness", "/debug/android/foreground",
            "/debug/sessions", "/debug/policy", "/debug/engine",
        ))
    }
}
