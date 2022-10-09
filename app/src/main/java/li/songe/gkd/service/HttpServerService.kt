package li.songe.gkd.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ServiceUtils
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import li.songe.gkd.App
import li.songe.gkd.data.api.WindowData
import li.songe.gkd.store.Storage
import java.net.NetworkInterface

class HttpServerService : Service() {
    companion object {
        fun isRunning() = ServiceUtils.isServiceRunning(HttpServerService::class.java)
        fun stop(context: Context = App.context) {
            if (isRunning()) {
                context.stopService(Intent(context, HttpServerService::class.java))
            }
        }

        fun start(context: Context = App.context) {
            context.startService(Intent(context, HttpServerService::class.java))
        }

        fun getIpAddressInLocalNetwork(): String {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces().iterator().asSequence()
            val localAddresses = networkInterfaces.flatMap {
                it.inetAddresses.asSequence()
                    .filter { inetAddress ->
                        inetAddress.isSiteLocalAddress
                                && !(inetAddress.hostAddress?.contains(":") ?: false)
                                && inetAddress.hostAddress != "127.0.0.1"
                    }
                    .map { inetAddress -> inetAddress.hostAddress }
            }
            return localAddresses.firstOrNull() ?: "127.0.0.1"
        }

        fun getHttpUrl() =
            "http://${getIpAddressInLocalNetwork()}:${Storage.settings.httpServerPort}/"


        private val RpcErrorHeaderPlugin = createApplicationPlugin(name = "RpcErrorHeaderPlugin") {
            onCall { call ->
                call.request.origin.apply {
                    Log.d("Ktor", "Request URL: $scheme://$host:$port$uri")
                }
            }
            onCallRespond { call, body ->
                call.response.header("K-Rpc-Error", if (body is RpcError) 1 else 0)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            LogUtils.d("http://${getIpAddressInLocalNetwork()}:${Storage.settings.httpServerPort}")
            server.start(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.launch(Dispatchers.IO) {
            server.stop(1000, 2000)
            scope.cancel()
            LogUtils.d("http server is stopped")
        }
    }


    private val scope by lazy { CoroutineScope(Dispatchers.IO) }

    private val server by lazy {/**/embeddedServer(Netty, Storage.settings.httpServerPort) {
        install(CORS) {
            anyHost()
        }
        install(RpcErrorHeaderPlugin)
        install(ContentNegotiation) {
            json()
        }
        routing {
            route("/api/rpc") {
                post("/window") {
                    if (GkdAccessService.isRunning()) {
                        call.respond(WindowData.singleton)
                        return@post
                    }
                    call.respond(RpcError("AccessibilityService is unavailable"))
                }

                post("/screenshot") {
//                    要求相同进程
                    val bitmap = ScreenshotService.screenshot()
                    if (bitmap != null) {
                        call.respondOutputStream(ContentType.Image.PNG) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
                            bitmap.recycle()
                        }
                        return@post
                    }
                    call.respond(RpcError("ScreenshotService is unavailable"))
                }
            }

            get("/") {
                call.respondText("<html>hello world</html>", ContentType.Text.Html)
            }
        }
    }
    }
}


