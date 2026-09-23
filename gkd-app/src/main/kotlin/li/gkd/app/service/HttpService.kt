package li.gkd.app.service

import android.util.Log
import androidx.lifecycle.lifecycleScope
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.A11yRuntime
import li.gkd.app.appScope
import li.gkd.app.data.AppInfo
import li.gkd.app.data.DeviceInfo
import li.gkd.app.data.GkdAction
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.RpcError
import li.gkd.db.SubsItem
import li.gkd.app.data.selfAppInfo
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.db.LOCAL_HTTP_SUBS_ID
import li.gkd.app.util.LogUtils
import li.gkd.app.util.SERVER_SCRIPT_URL
import li.gkd.app.snapshot.SnapshotCapture
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.util.NetworkUtils
import li.gkd.app.util.keepNullJson
import li.gkd.app.util.launchLogged
import li.gkd.app.util.mapState
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.ToastUtils.toast


class HttpService : LifecycleHookService() {
    val httpServerPortFlow by lazy {
        storeFlow.mapState(lifecycleScope) { s -> s.httpServerPort }
    }
    init {
        useLogLifecycle()
        useServicePresence(
            stateFlow = isRunning,
            name = UiStrings.http_service_compact_label,
        )
        useStopServiceReceiver()
        onDestroyed {
            if (storeFlow.value.autoClearMemorySubs) {
                appScope.launchLogged(Dispatchers.IO) {
                    SubscriptionRepository.delete(LOCAL_HTTP_SUBS_ID)
                }
            }
        }
        onCreated {
            NotificationCatalog.http(httpServerPortFlow.value).startForeground()
            var startedOnce = false
            lifecycleScope.launchLogged(Dispatchers.IO) {
                httpServerPortFlow.collectLatest { port ->
                    if (!NetworkUtils.isPortAvailable(port)) {
                        toast(UiStrings.http_port_occupied(port))
                        stopSelf()
                        return@collectLatest
                    }
                    val server = try {
                        createServer(port).apply { start() }
                    } catch (e: Exception) {
                        toast(UiStrings.http_service_start_failed(e.stackTraceToString()))
                        LogUtils.d("HTTP服务启动失败", e)
                        stopSelf()
                        return@collectLatest
                    }
                    httpServerFlow.value = server
                    val localNetworkIps = NetworkUtils.getIpAddressInLocalNetwork()
                    localNetworkIpsFlow.value = localNetworkIps
                    NotificationCatalog.http(port, localNetworkIps).startForeground()
                    if (startedOnce) {
                        toast(UiStrings.http_service_restart_success)
                    }
                    startedOnce = true
                    try {
                        awaitCancellation()
                    } finally {
                        httpServerFlow.compareAndSet(server, null)
                        server.stop()
                    }
                }
            }
        }
    }

    companion object {
        val httpServerFlow: StateFlow<ServerType?>
            field = MutableStateFlow(null)
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)
        val localNetworkIpsFlow: StateFlow<List<String>>
            field = MutableStateFlow(emptyList())
        fun stop() = IntentUtils.stopServiceByClass(HttpService::class)
        fun start() = IntentUtils.startForegroundServiceByClass(HttpService::class)

    }
}

typealias ServerType = EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>


@Serializable
data class RpcOk(
    val message: String? = null,
)

@Serializable
data class ReqId(
    val id: Long,
)

@Serializable
data class ServerInfo(
    val device: DeviceInfo = DeviceInfo(),
    val gkdAppInfo: AppInfo = selfAppInfo
)

fun clearHttpSubs() {
    // 如果 app 被直接在任务列表划掉, HTTP订阅会没有清除, 所以在后续的第一次启动时清除
    if (HttpService.isRunning.value) return
    appScope.launchLogged {
        delay(1000)
        if (storeFlow.value.autoClearMemorySubs) {
            SubscriptionRepository.delete(LOCAL_HTTP_SUBS_ID)
        }
    }
}

private val httpSubsItem = SubsItem(
    id = LOCAL_HTTP_SUBS_ID,
    order = -1,
    enableUpdate = false,
)

private fun createServer(port: Int) = embeddedServer(CIO, port) {
    install(getKtorCorsPlugin())
    install(getKtorErrorPlugin())
    install(ContentNegotiation) { json(keepNullJson) }
    routing {
        get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$SERVER_SCRIPT_URL'></script>" } }
        route("/api") {
            post("/getServerInfo") { call.respond(ServerInfo()) }
            post("/getSnapshot") {
                val data = call.receive<ReqId>()
                val fp = SnapshotRepository.snapshotFile(data.id)
                if (!fp.exists()) {
                    throw RpcError(UiStrings.snapshot_not_found)
                }
                call.respondFile(fp)
            }
            post("/getScreenshot") {
                val data = call.receive<ReqId>()
                val fp = SnapshotRepository.screenshotFile(data.id)
                if (!fp.exists()) {
                    throw RpcError(UiStrings.screenshot_not_found)
                }
                call.respondFile(fp)
            }
            post("/captureSnapshot") {
                call.respond(SnapshotCapture.capture())
            }
            post("/getSnapshots") {
                val list = SnapshotRepository.snapshots().first().mapNotNull {
                    try {
                        SnapshotRepository.getMinSnapshot(it.id)
                    } catch (_: Throwable) {
                        null
                    }
                }
                call.respond(list)
            }
            post("/deleteSnapshot") {
                val data = call.receive<ReqId>()
                val allSnapshots = SnapshotRepository.snapshots().first()
                val snapshot = allSnapshots.find { it.id == data.id }
                if (snapshot != null) {
                    SnapshotRepository.delete(snapshot)
                    call.respond(RpcOk(UiStrings.snapshot_delete_success))
                } else {
                    throw RpcError(UiStrings.snapshot_missing_or_deleted)
                }
            }
            post("/updateSubscription") {
                val subscription =
                    RawSubscription.parse(call.receiveText(), json5 = false)
                        .copy(
                            id = LOCAL_HTTP_SUBS_ID,
                            name = UiStrings.subscription_memory,
                            version = 0,
                            author = "@gkd-kit/inspect"
                        )
                SubscriptionRepository.saveWithItem(subscription, httpSubsItem)
                call.respond(RpcOk())
            }
            post("/execSelector") {
                val gkdAction = call.receive<GkdAction>()
                call.respond(A11yRuntime.execAction(gkdAction))
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
            call.respond("all-cors-ok")
        }
    }
}

private fun getKtorErrorPlugin() = createApplicationPlugin(name = "KtorErrorPlugin") {
    onCall { call ->
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
