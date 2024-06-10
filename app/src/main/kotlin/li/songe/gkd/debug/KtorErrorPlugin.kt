package li.songe.gkd.debug

import android.util.Log
import com.blankj.utilcode.util.LogUtils
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.plugins.origin
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import li.songe.gkd.data.RpcError

val KtorErrorPlugin = createApplicationPlugin(name = "KtorErrorPlugin") {
    onCall { call ->
        // TODO 在局域网会被扫描工具批量请求多个路径
        if (call.request.uri == "/" || call.request.uri.startsWith("/api/")) {
            Log.d("Ktor", "onCall: ${call.request.origin.remoteAddress} -> ${call.request.uri}")
        }
    }
    on(CallFailed) { call, cause ->
        when (cause) {
            is RpcError -> {
                // 主动抛出的错误
                LogUtils.d(call.request.uri, cause.message)
                call.respond(cause)
            }

            is Exception -> {
                // 未知错误
                LogUtils.d(call.request.uri, cause.message)
                cause.printStackTrace()
                call.respond(RpcError(message = cause.message ?: "unknown error", unknown = true))
            }

            else -> {
                cause.printStackTrace()
            }
        }
    }
}