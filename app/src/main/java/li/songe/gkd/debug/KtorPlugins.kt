package li.songe.gkd.debug

import android.util.Log
import com.blankj.utilcode.util.LogUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import li.songe.gkd.data.RpcError

val RpcErrorHeaderPlugin = createApplicationPlugin(name = "RpcErrorHeaderPlugin") {
    onCall { call ->
        Log.d("Ktor", "onCall: ${call.request.uri}")
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
                call.respond(HttpStatusCode.InternalServerError, cause)
            }

            else -> {
                cause.printStackTrace()
            }
        }
    }
    onCallRespond { call, _ ->
        call.response.header("Access-Control-Expose-Headers", "*")
        call.response.header("Access-Control-Allow-Private-Network", "true")
    }
}