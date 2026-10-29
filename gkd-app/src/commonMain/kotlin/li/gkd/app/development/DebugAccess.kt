package li.gkd.app.development

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.request.path
import io.ktor.server.response.respond

/** Install before browser CORS so its OPTIONS response cannot bypass diagnostic authentication. */
fun Application.configureDebugAccess(token: String, stopping: () -> Boolean) {
    install(
        createApplicationPlugin("DebugAccess") {
            onCall { call ->
                val path = call.request.path()
                if (path == "/health" || path == "/debug" || path.startsWith("/debug/")) {
                    if (stopping()) {
                        call.respond(HttpStatusCode.ServiceUnavailable)
                    } else if (call.request.headers["Authorization"] != "Bearer $token") {
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }
        },
    )
}
