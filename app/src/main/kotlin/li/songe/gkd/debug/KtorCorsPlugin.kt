package li.songe.gkd.debug

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond

// allow all cors
val KtorCorsPlugin = createApplicationPlugin(name = "KtorCorsPlugin") {
    onCallRespond { call, _ ->
        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        call.response.header(HttpHeaders.AccessControlAllowMethods, "*")
        call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
        call.response.header(HttpHeaders.AccessControlExposeHeaders, "*")
        call.response.header("Access-Control-Allow-Private-Network", "true")
    }
    onCall { call ->
        if (call.request.httpMethod == HttpMethod.Options) {
            call.respond("all-cors-ok")
        }
    }
}