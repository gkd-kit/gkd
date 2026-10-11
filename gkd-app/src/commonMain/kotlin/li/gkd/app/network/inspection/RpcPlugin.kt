package li.gkd.app.network.inspection

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.origin
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import li.gkd.app.error.BusinessError
import li.gkd.app.resources.*
import li.gkd.app.util.LogUtils
import org.jetbrains.compose.resources.getString

fun getRpcPlugin(jsonFormat: Json) =
    createApplicationPlugin(name = "RpcPlugin") {
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
                is BusinessError -> RpcError(
                    code = cause.errorCode,
                    message = cause.errorMessage.resolve(),
                    path = cause.errorPath,
                )
                // ktor error
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
