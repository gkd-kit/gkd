package li.songe.gkd.debug

import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ServiceUtils
import io.ktor.http.CacheControl
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import li.songe.gkd.app
import li.songe.gkd.composition.CompositionService
import li.songe.gkd.data.DeviceInfo
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt.captureSnapshot
import li.songe.gkd.util.Ext.getIpAddressInLocalNetwork
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.storeFlow
import java.io.File

class HttpService : CompositionService({
    val scope = CoroutineScope(Dispatchers.IO)
    subsFlow.value = null
    val server =
        embeddedServer(Netty, storeFlow.value.httpServerPort, configure = { tcpKeepAlive = true }) {
            install(CORS) { anyHost() }
            install(RpcErrorHeaderPlugin)
            install(ContentNegotiation) { json() }

            routing {
                get("/") { call.respond("hello world") }
                route("/api") {
                    get("/device") { call.respond(DeviceInfo.instance) }
                    get("/snapshot") {
                        val id = call.request.queryParameters["id"]?.toLongOrNull()
                            ?: throw RpcError("miss id")
                        val fp = File(SnapshotExt.getSnapshotPath(id))
                        if (!fp.exists()) {
                            throw RpcError("对应快照不存在")
                        }
                        call.response.cacheControl(CacheControl.MaxAge(3600 * 24 * 7))
                        call.respondFile(fp)
                    }
                    get("/screenshot") {
                        val id = call.request.queryParameters["id"]?.toLongOrNull()
                            ?: throw RpcError("miss id")
                        val fp = File(SnapshotExt.getScreenshotPath(id))
                        if (!fp.exists()) {
                            throw RpcError("对应截图不存在")
                        }
                        call.response.cacheControl(CacheControl.MaxAge(3600 * 24 * 7))
                        call.respondFile(fp)
                    }
                    get("/captureSnapshot") {
                        call.respond(captureSnapshot())
                    }
                    get("/snapshots") {
                        call.respond(DbSet.snapshotDao.query().first())
                    }
                    get("/subsApps") {
                        call.respond(subsFlow.value?.apps ?: emptyList())
                    }
                    post("/updateSubsApps") {
                        val subsStr =
                            """{"name":"GKD-内存订阅","id":-1,"version":0,"author":"@gkd-kit/inspect","apps":${call.receive<String>()}}"""
                        try {
                            subsFlow.value = SubscriptionRaw.parse(subsStr)
                        } catch (e: Exception) {
                            throw RpcError(e.message ?: "未知")
                        }
                        call.respond("")
                    }
                }
            }
        }
    scope.launchTry(Dispatchers.IO) {
        LogUtils.d(*getIpAddressInLocalNetwork().map { host -> "http://${host}:${storeFlow.value.httpServerPort}" }
            .toList().toTypedArray())
        server.start(true)
    }
    onDestroy {
        subsFlow.value = null
        scope.launchTry(Dispatchers.IO) {
            server.stop()
            LogUtils.d("http server is stopped")
            scope.cancel()
        }
    }
}) {
    companion object {

        val subsFlow by lazy { MutableStateFlow<SubscriptionRaw?>(null) }


        fun isRunning() = ServiceUtils.isServiceRunning(HttpService::class.java)
        fun stop(context: Context = app) {
            if (isRunning()) {
                context.stopService(Intent(context, HttpService::class.java))
            }
        }

        fun start(context: Context = app) {
            context.startService(Intent(context, HttpService::class.java))
        }

    }
}