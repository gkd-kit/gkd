package li.songe.gkd.debug

import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ServiceUtils
import com.blankj.utilcode.util.ToastUtils
import io.ktor.http.CacheControl
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.cacheControl
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
import li.songe.gkd.composition.CompositionExt.useMessage
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.composition.InvokeMessage
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.util.Ext.getIpAddressInLocalNetwork
import li.songe.gkd.util.Storage
import java.io.File

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
                        ToastUtils.showShort("保存快照成功")
                    } catch (e: Exception) {
                        ToastUtils.showShort("保存快照失败")
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
            route("/api") {
                get("/device") { call.respond(DeviceSnapshot.instance) }
                get("/snapshotIds") {
                    call.respond(SnapshotExt.getSnapshotIds())
                }
                get("/snapshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                    if (id != null) {
                        val fp = File(SnapshotExt.getSnapshotPath(id))
                        if (!fp.exists()) {
                            throw RpcError("对应快照不存在")
                        }
                        call.response.cacheControl(CacheControl.MaxAge(3600))
                        call.respondFile(fp)
                    } else {
                        removeBubbles()
                        delay(200)
                        try {
                            call.respond(captureSnapshot())
                        } catch (e: Exception) {
                            throw e
                        } finally {
                            showBubbles()
                        }
                    }
                }
                get("/screenshot") {
                    val id = call.request.queryParameters["id"]?.toLongOrNull()
                        ?: throw RpcError("miss id")
                    val fp = File(SnapshotExt.getScreenshotPath(id))
                    if (!fp.exists()) {
                        throw RpcError("对应截图不存在")
                    }
                    call.response.cacheControl(CacheControl.MaxAge(3600))
                    call.respondFile(fp)
                }
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

    }
}