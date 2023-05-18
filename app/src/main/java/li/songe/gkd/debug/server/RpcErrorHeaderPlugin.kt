package li.songe.gkd.debug.server

import android.util.Log
import com.blankj.utilcode.util.LogUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond

val RpcErrorHeaderPlugin = createApplicationPlugin(name = "RpcErrorHeaderPlugin") {
    onCall { call ->
        Log.d("Ktor", "onCall: ${call.request.uri}")
    }
    on(CallFailed) { call, cause ->
        when (cause) {
            is RpcError -> {
                // 主动抛出的错误
                LogUtils.d(call.request.uri, cause.code, cause.message)
                call.response.header(RpcError.HeaderKey, RpcError.HeaderErrorValue)
                call.respond(cause)
            }

            is Exception -> {
                // 未知错误
                LogUtils.d(call.request.uri, cause.message)
                cause.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, cause)
            }

            else -> {
                cause.printStackTrace()
            }
        }
    }
    onCallRespond { call, _ ->
        val status=call.response.status() ?: HttpStatusCode.OK
        if (status == HttpStatusCode.OK &&
            !call.response.headers.contains(
                RpcError.HeaderKey
            )
        ) {
            call.response.header(RpcError.HeaderKey, RpcError.HeaderOkValue)
        }
    }
}