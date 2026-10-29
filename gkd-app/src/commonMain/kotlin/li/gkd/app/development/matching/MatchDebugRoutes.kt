package li.gkd.app.development.matching

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchDebugSnapshot
import li.gkd.app.matching.diagnostics.MatchDiagnosticState
import li.gkd.app.matching.diagnostics.matchForegroundSnapshot
import li.gkd.app.util.JsonUtils

@Serializable
data class MatchDebugStart(
    val detailed: Boolean = false,
    val ruleFilter: String? = null,
    val recordQueries: Boolean = false,
    val recordSystem: Boolean = false,
)

fun matchDebugSnapshot(coordinator: MatchCoordinator = MatchingRuntime.coordinator) =
    MatchDebugSnapshot(coordinator.snapshot(), matchForegroundSnapshot())

/** Transport only; UI and HTTP observe the exact same diagnostic session. */
fun Route.matchDebugRoutes(
    coordinator: MatchCoordinator = MatchingRuntime.coordinator,
    onSessionStarted: (String) -> Unit = {},
    startCapture: suspend (MatchDebugStart) -> MatchDiagnosticState = { request ->
        coordinator.startDiagnostics(
            request.detailed,
            request.ruleFilter,
            request.recordQueries,
            request.recordSystem,
        )
    },
) =
    route("/debug") {
        debugPost("/sessions/{id}/runs") {
            call.respond(
                MatchDebugRuns.start(requireNotNull(call.parameters["id"]), call.receive()),
            )
        }
        debugGet("/sessions/{id}/runs") {
            val id = requireNotNull(call.parameters["id"])
            call.respond(MatchDebugRuns.snapshot(id))
        }
        debugGet("/sessions/{id}/runs/{runId}") {
            call.respond(
                MatchDebugRuns.get(
                    requireNotNull(call.parameters["id"]),
                    requireNotNull(call.parameters["runId"]),
                ),
            )
        }
        debugPost("/sessions/{id}/runs/{runId}/cancel") {
            call.respond(
                MatchDebugRuns.cancel(
                    requireNotNull(call.parameters["id"]),
                    requireNotNull(call.parameters["runId"]),
                ),
            )
        }
        debugPost("/sessions") {
            val request = call.receive<MatchDebugStart>()
            val session = startCapture(request)
            onSessionStarted(session.id)
            call.respond(session)
        }
        debugGet("/policy") { call.respond(coordinator.policy.value) }
        debugGet("/engine") { call.respond(matchDebugSnapshot(coordinator)) }
        debugPost("/policy") {
            coordinator.updateDiagnosticPolicy(call.receive())
            call.respond(coordinator.policy.value)
        }
        debugGet("/sessions/{id}") {
            val id = requireNotNull(call.parameters["id"])
            call.respond(coordinator.diagnostics.session(id))
        }
        debugGet("/sessions/{id}/events") {
            call.respond(
                coordinator.diagnostics.read(
                    requireNotNull(call.parameters["id"]),
                    call.request.queryParameters["after"]?.toLong() ?: 0,
                    call.request.queryParameters["limit"]?.toInt() ?: 200,
                ),
            )
        }
        debugPost("/sessions/{id}/stop") {
            val id = requireNotNull(call.parameters["id"])
            coordinator.stopDiagnostics(id)
            call.respond(coordinator.diagnostics.session(id))
        }
        debugPost("/sessions/{id}/export") {
            val id = requireNotNull(call.parameters["id"])
            val capture = coordinator.exportDiagnostics(id)
            call.respond(capture)
        }
        debugGet("/sessions/{id}/stream") {
            val id = requireNotNull(call.parameters["id"])
            var cursor =
                call.request.headers["Last-Event-ID"]?.toLong()
                    ?: call.request.queryParameters["after"]?.toLong()
                    ?: 0
            coordinator.diagnostics.read(id, cursor)
            call.respondTextWriter(ContentType.Text.EventStream) {
                while (true) {
                    val page = coordinator.diagnostics.read(id, cursor)
                    write(
                        "id: ${page.cursor}\nevent: records\ndata: ${JsonUtils.keepingNulls.encodeToString(page)}\n\n",
                    )
                    flush()
                    cursor = page.cursor
                    if (page.trackingComplete && !page.hasMore) break
                    delay(200)
                }
            }
        }
    }

private fun Route.debugGet(path: String, block: suspend RoutingContext.() -> Unit) =
    get(path) { diagnosticRequest(block) }

private fun Route.debugPost(path: String, block: suspend RoutingContext.() -> Unit) =
    post(path) { diagnosticRequest(block) }

private suspend fun RoutingContext.diagnosticRequest(block: suspend RoutingContext.() -> Unit) {
    try {
        block()
    } catch (error: IllegalArgumentException) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to (error.message ?: "Invalid request")),
        )
    } catch (error: IllegalStateException) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        call.respond(
            HttpStatusCode.Conflict,
            mapOf("error" to (error.message ?: "Invalid engine state")),
        )
    }
}
