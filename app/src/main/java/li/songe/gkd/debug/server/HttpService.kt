package li.songe.gkd.debug.server

import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ServiceUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.uri
import io.ktor.server.response.cacheControl
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.App
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.composition.Hook.useMessage
import li.songe.gkd.composition.InvokeMessage
import li.songe.gkd.debug.Ext.captureSnapshot
import li.songe.gkd.debug.Ext.screenshotDir
import li.songe.gkd.debug.Ext.snapshotDir
import li.songe.gkd.debug.Ext.windowDir
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.util.Ext.getIpAddressInLocalNetwork
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.Storage

class HttpService : CompositionService({
    val scope = CoroutineScope(Dispatchers.IO)
    val (onMessage, sendMessage) = useMessage(this::class.simpleName)
    val removeBubbles = {
        sendMessage(InvokeMessage(FloatingService::class.simpleName, "removeBubbles"))
    }
    val showBubbles = {
        sendMessage(InvokeMessage(FloatingService::class.simpleName, "showBubbles"))
    }
    onMessage { message ->
        when (message.method) {
            "capture" -> {
                scope.launch {
                    removeBubbles()
                    delay(200)
                    try {
                        captureSnapshot()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    showBubbles()
                }
            }
        }
    }
    val server = embeddedServer(
        Netty,
        Storage.settings.httpServerPort,
        configure = { tcpKeepAlive = true }
    ) {
        install(CORS) { anyHost() }
        install(RpcErrorHeaderPlugin)
        install(ContentNegotiation) { json() }

        routing {
            route("/api/rpc") {
                get("/capture") {
                    removeBubbles()
                    delay(200)
                    try {
                        call.respond(captureSnapshot())
                    } catch (e: Exception) {
                        showBubbles()
                        throw e
                    }
                    showBubbles()
                }
                get("/snapshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    call.response.cacheControl(CacheControl.MaxAge(3600))
                    call.respondFile(snapshotDir, "/${id}.json")
                }

                get("/window") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    call.response.cacheControl(CacheControl.MaxAge(3600))
                    call.respondFile(windowDir, "/${id}.json")
                }

                get("/screenshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    call.response.cacheControl(CacheControl.MaxAge(3600))
                    call.respondFile(screenshotDir, "/${id}.png")
                }
            }

            listOf("/", "/index.html").forEach { p ->
                get(p) {
                    val response = Singleton.client.get("$proxyUrl${call.request.uri}")
                    call.response.header(
                        HttpHeaders.ContentType, "text/html; charset=UTF-8"
                    )
                    call.respond(response.bodyAsText())
                }
            }
            get("/assets/*") {
                call.response.header(
                    HttpHeaders.Location,
                    "$proxyUrl${context.request.uri}"
                )
                call.respond(HttpStatusCode.Found)
            }
        }
    }
    scope.launch {
        LogUtils.d(*getIpAddressInLocalNetwork().map { host -> "http://${host}:${Storage.settings.httpServerPort}" }
            .toList().toTypedArray())
        server.start(true)
    }
    onDestroy {
        scope.launch(Dispatchers.IO) {
            server.stop(1000, 2000)
            scope.cancel()
            LogUtils.d("http server is stopped")
        }
    }
}) {
    companion object {
        fun isRunning() = ServiceUtils.isServiceRunning(HttpService::class.java)
        fun stop(context: Context = App.context) {
            if (isRunning()) {
                context.stopService(Intent(context, HttpService::class.java))
            }
        }

        fun start(context: Context = App.context) {
            context.startService(Intent(context, HttpService::class.java))
        }

        private const val proxyUrl = "https://gkd-ui-viewer.netlify.app"
    }
}