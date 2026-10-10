package li.gkd.app.network.inspection

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import li.gkd.app.inspection.InspectionException
import li.gkd.app.inspection.InspectionFailure
import li.gkd.app.inspection.userMessage
import li.gkd.app.model.ActionResult
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.GkdAction
import li.gkd.app.resources.*
import li.gkd.app.snapshot.SnapshotCaptureBusyException
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionParseException
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import li.gkd.db.SubsItem
import org.jetbrains.compose.resources.getString
import java.io.IOException

private val httpSubsItem = SubsItem(
    id = LOCAL_HTTP_SUBS_ID,
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
    install(getInspectionRpcPlugin(jsonFormat))
    install(ContentNegotiation) { json(jsonFormat) }
    routing {
        get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$scriptUrl'></script>" } }
        route("/api") {
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
                            id = LOCAL_HTTP_SUBS_ID,
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

private fun getInspectionRpcPlugin(jsonFormat: Json) =
    createApplicationPlugin(name = "InspectionRpcPlugin") {
        onCall { call ->
            if (call.request.uri == "/" || call.request.uri.startsWith("/api/")) {
                LogUtils.d("onCall: ${call.request.origin.remoteAddress} -> ${call.request.uri}")
            }
        }
        on(ResponseBodyReadyForSend) { call, body ->
            val status = body.status ?: call.response.status() ?: HttpStatusCode.OK
            if (call.request.uri.startsWith("/api/") &&
                status == HttpStatusCode.OK && !body.headers.contains("GKD-RPC-Status")
            ) {
                transformBodyTo(body.withRpcStatus("ok"))
            }
        }
        on(CallFailed) { call, cause ->
            if (cause is CancellationException || cause !is Exception ||
                !call.request.uri.startsWith("/api/") || call.response.isCommitted
            ) {
                throw cause
            }
            val error = when (cause) {
                is InspectionException -> RpcError(
                    code = cause.reason.rpcCode,
                    message = cause.userMessage(),
                )
                is SubscriptionParseException -> RpcError(
                    code = "INVALID_SUBSCRIPTION",
                    message = "${getString(Res.string.rpc_invalid_subscription)}: ${cause.detail}",
                    path = cause.path,
                )
                is SnapshotCaptureBusyException -> RpcError(
                    "SNAPSHOT_CAPTURE_BUSY", getString(Res.string.snapshot_save_in_progress)
                )
                is BadRequestException, is CannotTransformContentToTypeException,
                is UnsupportedMediaTypeException -> RpcError(
                    "INVALID_REQUEST", getString(Res.string.rpc_invalid_request)
                )
                else -> RpcError(
                    code = "INTERNAL_ERROR",
                    message = getString(Res.string.rpc_internal_error),
                    stackTrace = cause.stackTraceToString(),
                )
            }
            if (error.code == "INTERNAL_ERROR") {
                LogUtils.d("${call.request.uri}: ${error.code}", cause)
            } else {
                LogUtils.d("${call.request.uri}: ${error.code}: ${error.message}")
            }
            call.respond(
                TextContent(jsonFormat.encodeToString(error), ContentType.Application.Json, HttpStatusCode.OK)
                    .withRpcStatus("error")
            )
        }
    }

private fun OutgoingContent.withRpcStatus(rpcStatus: String): OutgoingContent {
    val original = this
    val rpcHeaders = Headers.build {
        appendAll(original.headers)
        set("GKD-RPC-Status", rpcStatus)
    }
    // Ktor merges content headers only when committing this response. Failed content leaves no RPC header behind.
    // Concrete content variants are required: the engine unwraps ContentWrapper before reading its headers.
    return when (this) {
        is OutgoingContent.ByteArrayContent -> object : OutgoingContent.ByteArrayContent() {
            override val contentType get() = original.contentType
            override val contentLength get() = original.contentLength
            override val status get() = original.status
            override val headers = rpcHeaders
            override fun bytes() = original.bytes()
            override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
            override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
            override fun trailers() = original.trailers()
        }
        is OutgoingContent.ReadChannelContent -> object : OutgoingContent.ReadChannelContent() {
            override val contentType get() = original.contentType
            override val contentLength get() = original.contentLength
            override val status get() = original.status
            override val headers = rpcHeaders
            override fun readFrom() = original.readFrom()
            override fun readFrom(range: LongRange) = original.readFrom(range)
            override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
            override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
            override fun trailers() = original.trailers()
        }
        is OutgoingContent.WriteChannelContent -> object : OutgoingContent.WriteChannelContent() {
            override val contentType get() = original.contentType
            override val contentLength get() = original.contentLength
            override val status get() = original.status
            override val headers = rpcHeaders
            override suspend fun writeTo(channel: ByteWriteChannel) = original.writeTo(channel)
            override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
            override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
            override fun trailers() = original.trailers()
        }
        is OutgoingContent.NoContent -> object : OutgoingContent.NoContent() {
            override val contentType get() = original.contentType
            override val contentLength get() = original.contentLength
            override val status get() = original.status
            override val headers = rpcHeaders
            override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
            override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
            override fun trailers() = original.trailers()
        }
        is OutgoingContent.ContentWrapper -> copy(delegate().withRpcStatus(rpcStatus))
        is OutgoingContent.ProtocolUpgrade -> this
    }
}

private val InspectionFailure.rpcCode: String
    get() = when (this) {
        InspectionFailure.SnapshotNotFound, InspectionFailure.SnapshotMissingOrDeleted -> "SNAPSHOT_NOT_FOUND"
        InspectionFailure.ScreenshotNotFound -> "SCREENSHOT_NOT_FOUND"
        InspectionFailure.InvalidSelector, InspectionFailure.SelectorTypeError -> "INVALID_SELECTOR"
        InspectionFailure.AccessibilityUnavailable,
        InspectionFailure.AccessibilityAuthorizationRequired -> "ACCESSIBILITY_UNAVAILABLE"
        InspectionFailure.ScreenNodesUnavailable,
        InspectionFailure.SnapshotNodesUnavailable -> "SCREEN_NODES_UNAVAILABLE"
        InspectionFailure.TargetNodeNotFound -> "TARGET_NODE_NOT_FOUND"
        InspectionFailure.PlatformUnsupported -> "PLATFORM_UNSUPPORTED"
    }
