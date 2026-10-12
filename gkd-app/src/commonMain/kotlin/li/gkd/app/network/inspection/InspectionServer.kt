package li.gkd.app.network.inspection

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import li.gkd.app.inspection.InspectionException
import li.gkd.app.inspection.InspectionFailure
import li.gkd.app.model.ActionResult
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.GkdAction
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.SubscriptionIds
import li.gkd.db.SubsItem
import org.jetbrains.compose.resources.getString
import java.io.IOException

private val httpSubsItem = SubsItem(
    id = SubscriptionIds.LOCAL_HTTP,
    order = -1,
    enableUpdate = false,
)

fun Application.configureInspectionServer(
    serverInfo: () -> ServerInfo,
    captureSnapshot: suspend () -> ComplexSnapshot,
    execSelector: suspend (GkdAction) -> ActionResult,
    scriptUrl: String,
    jsonFormat: Json,
) {
    install(getKtorCorsPlugin())
    install(getRpcPlugin(jsonFormat))
    install(ContentNegotiation) { json(jsonFormat) }
    routing {
        get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$scriptUrl'></script>" } }
        route("/api") {
            registerSubscriptionRoutes()
            post("/getServerInfo") { call.respond(serverInfo()) }
            post("/getSnapshot") {
                val data = call.receive<ReqId>()
                val fp = SnapshotStore.snapshotFile(data.id)
                if (!fp.exists()) {
                    throw InspectionException(InspectionFailure.SnapshotNotFound)
                }
                call.respondFile(fp)
            }
            post("/getScreenshot") {
                val data = call.receive<ReqId>()
                val fp = SnapshotStore.screenshotFile(data.id)
                if (!fp.exists()) {
                    throw InspectionException(InspectionFailure.ScreenshotNotFound)
                }
                call.respondFile(fp)
            }
            post("/captureSnapshot") {
                call.respond(captureSnapshot())
            }
            post("/getSnapshots") {
                val list = Db.snapshotDao.query().first().mapNotNull {
                    try {
                        SnapshotStore.getMinSnapshot(it.id)
                    } catch (e: IOException) {
                        LogUtils.d("Cannot read snapshot ${it.id}", e)
                        null
                    } catch (e: SerializationException) {
                        LogUtils.d("Invalid snapshot ${it.id}", e)
                        null
                    }
                }
                call.respond(list)
            }
            post("/deleteSnapshot") {
                val data = call.receive<ReqId>()
                val allSnapshots = Db.snapshotDao.query().first()
                val snapshot = allSnapshots.find { it.id == data.id }
                if (snapshot != null) {
                    SnapshotStore.delete(snapshot)
                    call.respond(rpcSuccess)
                } else {
                    throw InspectionException(InspectionFailure.SnapshotMissingOrDeleted)
                }
            }
            post("/updateSubscription") {
                val source = call.receiveText()
                val subscription =
                    RawSubscription.parse(source, json5 = false)
                        .copy(
                            id = SubscriptionIds.LOCAL_HTTP,
                            name = getString(Res.string.subscription_memory),
                            version = 0,
                            author = "@gkd-kit/inspect"
                        )
                SubscriptionRepository.saveWithItem(subscription, httpSubsItem)
                call.respond(rpcSuccess)
            }
            post("/execSelector") {
                val gkdAction = call.receive<GkdAction>()
                call.respond(execSelector(gkdAction))
            }
        }
    }
}

private fun getKtorCorsPlugin() = createApplicationPlugin(name = "KtorCorsPlugin") {
    onCall { call ->
        mapOf(
            HttpHeaders.AccessControlAllowOrigin to "*",
            HttpHeaders.AccessControlAllowMethods to "*",
            HttpHeaders.AccessControlAllowHeaders to "*",
            HttpHeaders.AccessControlExposeHeaders to "*",
            "Access-Control-Allow-Private-Network" to "true",
        ).forEach { (k, v) ->
            if (!call.response.headers.contains(k)) {
                call.response.header(k, v)
            }
        }
        if (call.request.httpMethod == HttpMethod.Options) {
            call.respond(rpcSuccess)
        }
    }
}
