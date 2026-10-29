package li.gkd.app

import androidx.compose.ui.awt.ComposeWindow
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.awt.Frame
import java.awt.Point
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowEvent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.model.AppInventory
import li.gkd.app.snapshot.SnapshotCaptureOutcome
import li.gkd.app.window.DesktopWindowGeometry

private object Edt : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext) =
        !SwingUtilities.isEventDispatchThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) =
        SwingUtilities.invokeLater(block)
}

@Serializable
data class UiNode(
    val id: String,
    val name: String?,
    val role: String,
    val enabled: Boolean,
    val editable: Boolean,
    val states: List<String>,
    val actions: List<String>,
    val children: List<UiNode>,
    val text: String? = null,
    val bounds: UiBounds? = null,
)

@Serializable data class UiBounds(val x: Int, val y: Int, val width: Int, val height: Int)

@Serializable
data class UiAction(
    val type: String,
    val node: String? = null,
    val action: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val text: String = "",
    val key: String = "",
    val amount: Int = 1,
    val window: String = "app",
    val area: String = "content",
    val endX: Int = 0,
    val endY: Int = 0,
)

@Serializable
data class DesktopWindowSnapshot(
    val customFrame: Boolean,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val contentX: Int,
    val contentY: Int,
    val contentWidth: Int,
    val contentHeight: Int,
    val minimized: Boolean,
    val maximized: Boolean,
)

@Serializable
data class DesktopCatalogSnapshot(
    val inventory: AppInventory?,
    val refreshing: Boolean,
    val failure: String?,
    val profileDirectory: String?,
)

/** Desktop controls only; window references belong to the host composition. */
fun Route.desktopControlRoutes(
    state: DesktopState,
    runtime: DesktopRuntime,
    window: ComposeWindow,
    awaitFrame: suspend () -> Unit,
    controlsWindow: () -> ComposeWindow?,
    hostKey: (String) -> Boolean,
) {
    fun target(id: String): ComposeWindow =
        when (id) {
            "app" -> window
            "controls" -> requireNotNull(controlsWindow()) { "Control window is closed" }
            else -> throw IllegalArgumentException("Unknown window: $id")
        }

    val windowNodes = mutableMapOf<String, MutableMap<String, Pair<AccessibleContext, String?>>>()
    get("/input-status") {
        call.respond(
            withContext(Edt) {
                mapOf(
                    "activations" to DesktopTestWindows.activations,
                    "focusedWindows" to Window.getWindows().count { it.isFocused },
                    "focusableWindows" to
                        Window.getWindows().count { it.isShowing && it.isFocusableWindow },
                )
            },
        )
    }
    get("/scenarios") {
        call.respond(
            mapOf(
                "pages" to DesktopState.pages,
                "variants" to DesktopState.variants,
            ),
        )
    }
    get("/state") { call.respond(withContext(Edt) { state.snapshot() }) }
    get("/app-catalog") {
        val catalog = AppInfoRepository.state.value
        call.respond(
            DesktopCatalogSnapshot(
                catalog.snapshot?.inventory,
                catalog.refreshing,
                catalog.failure?.message,
                DesktopStorage.profile.absolutePath.takeIf { DesktopStorage.isolated },
            ),
        )
    }
    post("/snapshot-capture/replay") {
        call.validated {
            val request = call.receive<DesktopSnapshotCaptureRequest>()
            val result = withContext(Edt) { runtime.replaySnapshot(request) }
            awaitFrame()
            when (result) {
                is SnapshotCaptureOutcome.Captured -> call.respond(result.value)
                SnapshotCaptureOutcome.Busy ->
                    call.respond(
                        io.ktor.http.HttpStatusCode.Conflict,
                        mapOf("error" to "SNAPSHOT_CAPTURE_BUSY"),
                    )
            }
        }
    }
    post("/runtime-records/replay") {
        call.validated {
            val request = call.receive<DesktopRuntimeRecordRequest>()
            val result = withContext(Edt) { runtime.replayRecords(request) }
            awaitFrame()
            call.respond(result)
        }
    }
    get("/services") { call.respond(state.simulator.settings.value.services) }
    post("/services/command") {
        call.validated {
            val request = call.receive<ServiceCommand>()
            if (request.service == SimulatedService.Debug && !request.enabled) {
                call.respond(HttpStatusCode.Accepted)
                SwingUtilities.invokeLater { runtime.services.setEnabled(request.service, false) }
                return@validated
            }
            withContext(Edt) { runtime.services.setEnabled(request.service, request.enabled) }
            awaitFrame()
            call.respond(state.simulator.settings.value.services)
        }
    }
    post("/services/event") {
        call.validated {
            val request = call.receive<ServiceEvent>()
            withContext(Edt) { runtime.services.event(request) }
            awaitFrame()
            call.respond(state.simulator.settings.value.services)
        }
    }
    get("/simulator") { call.respond(state.simulator.settings.value) }
    post("/simulator/patch") {
        call.validated {
            val request = call.receive<JsonObject>()
            withContext(Edt) { state.simulator.patch(request) }
            awaitFrame()
            call.respond(withContext(Edt) { state.snapshot() })
        }
    }
    get("/window") {
        call.validated {
            call.respond(
                withContext(Edt) {
                    val host = target(call.request.queryParameters["window"] ?: "app")
                    val frame = DesktopWindowGeometry.contentBounds(host, true)
                    val content = DesktopWindowGeometry.contentBounds(host)
                    DesktopWindowSnapshot(
                        DesktopWindowGeometry.isCustom(host),
                        frame.x,
                        frame.y,
                        frame.width,
                        frame.height,
                        content.x - frame.x,
                        content.y - frame.y,
                        content.width,
                        content.height,
                        host.extendedState and Frame.ICONIFIED != 0,
                        host.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH,
                    )
                },
            )
        }
    }
    post("/scenario") {
        call.validated {
            val request = call.receive<ScenarioRequest>()
            withContext(Edt) { state.load(request) }
            awaitFrame()
            call.respond(withContext(Edt) { state.snapshot() })
        }
    }
    post("/simulator") {
        call.validated {
            val request = call.receive<SimulatorSettings>()
            withContext(Edt) { state.simulator.replace(request) }
            awaitFrame()
            call.respond(withContext(Edt) { state.snapshot() })
        }
    }
    get("/semantics") {
        call.validated {
            call.respond(
                withContext(Edt) {
                    val windowId = call.request.queryParameters["window"] ?: "app"
                    val host = target(windowId)
                    val nodes = mutableMapOf<String, Pair<AccessibleContext, String?>>()
                    windowNodes[windowId] = nodes
                    val receiver = activeWindow(host)
                    readNode(
                        receiver.accessibleContext,
                        "root",
                        0,
                        nodes,
                        DesktopWindowGeometry.contentBounds(receiver).location,
                    )
                },
            )
        }
    }
    post("/action") {
        call.validated {
            val request = call.receive<UiAction>()
            when (request.type) {
                "close-window" ->
                    withContext(Edt) {
                        require(request.window == "controls") {
                            "Only the controls window can be closed through this endpoint"
                        }
                        val host = target(request.window)
                        host.dispatchEvent(
                            WindowEvent(
                                host,
                                WindowEvent.WINDOW_CLOSING,
                            ),
                        )
                    }

                "invoke",
                "text" ->
                    withContext(Edt) {
                        val context =
                            findNode(
                                activeWindow(target(request.window)).accessibleContext,
                                requireNotNull(request.node),
                            )
                        val snapshot = windowNodes[request.window]?.get(request.node)
                        require(
                            snapshot?.first === context &&
                                snapshot.second == context.accessibleName,
                        ) {
                            "Stale node path; read /desktop/semantics again"
                        }
                        require(context.accessibleStateSet.contains(AccessibleState.ENABLED)) {
                            "Node is disabled"
                        }
                        if (request.type == "text") {
                            val editable =
                                requireNotNull(context.accessibleEditableText) {
                                    "Node is not editable"
                                }
                            editable.setTextContents(request.text)
                        } else {
                            val action =
                                requireNotNull(context.accessibleAction) { "Node has no actions" }
                            require(request.action in 0 until action.accessibleActionCount)
                            require(action.doAccessibleAction(request.action)) {
                                "Action was rejected"
                            }
                        }
                    }

                "nativeKey" ->
                    throw IllegalArgumentException(
                        "Native input requires an isolated desktop; system input is disabled",
                    )
                "click",
                "rightClick",
                "doubleClick",
                "longClick",
                "drag",
                "hover",
                "scroll",
                "key" ->
                    withContext(
                        Edt,
                    ) {
                        val host = target(request.window)
                        val receiver = activeWindow(host, includeNonFocusable = false)
                        if (request.type == "key") {
                            if (
                                receiver === host &&
                                    request.window == "app" &&
                                    hostKey(
                                        request.key,
                                    )
                            )
                                Unit
                            else DesktopInput.key(receiver, request.key)
                        } else DesktopInput.pointer(receiver, request)
                    }

                else -> throw IllegalArgumentException("Unknown action")
            }
            awaitFrame()
            call.respond(withContext(Edt) { state.snapshot() })
        }
    }
    get("/screenshot") {
        call.validated {
            val host =
                withContext(Edt) {
                    target(
                        call.request.queryParameters["window"] ?: "app",
                    )
                }
            require(call.request.queryParameters["activate"] != "true") {
                "Screenshot activation is disabled"
            }
            awaitFrame()
            val area = call.request.queryParameters["area"] ?: "content"
            val mode = call.request.queryParameters["mode"] ?: "compose"
            require(area == "content" || area == "frame")
            require(mode == "compose" || mode == "screen")
            val picture =
                if (mode == "compose")
                    withContext(Edt) {
                        DesktopCapture.compose(host, area == "frame")
                    }
                else {
                    val bounds =
                        withContext(Edt) {
                            DesktopWindowGeometry.contentBounds(
                                host,
                                area == "frame",
                            )
                        }
                    Toolkit.getDefaultToolkit().sync()
                    Robot().createScreenCapture(bounds)
                }
            val bytes =
                ByteArrayOutputStream().use {
                    check(ImageIO.write(picture, "png", it))
                    it.toByteArray()
                }
            call.response.headers.append(
                "X-GKD-Capture",
                if (mode == "compose") "compose-recording" else "screen",
            )
            call.respondBytes(bytes, ContentType.Image.PNG)
        }
    }
}

private suspend fun ApplicationCall.validated(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
    } catch (e: IOException) {
        respond(
            HttpStatusCode.InternalServerError,
            mapOf("error" to (e.message ?: "Simulator storage unavailable")),
        )
    }
}

private fun readNode(
    context: AccessibleContext,
    path: String,
    depth: Int,
    nodes: MutableMap<String, Pair<AccessibleContext, String?>>,
    origin: Point,
): UiNode {
    nodes[path] = context to context.accessibleName
    val action = context.accessibleAction
    return UiNode(
        path,
        context.accessibleName,
        context.accessibleRole.toDisplayString(Locale.ROOT),
        context.accessibleStateSet.contains(AccessibleState.ENABLED),
        context.accessibleEditableText != null,
        context.accessibleStateSet.toArray().map { it.toDisplayString(Locale.ROOT) },
        (0 until (action?.accessibleActionCount ?: 0)).map {
            action!!.getAccessibleActionDescription(it).orEmpty()
        },
        if (depth >= 32) emptyList()
        else
            (0 until context.accessibleChildrenCount).mapNotNull { index ->
                context.getAccessibleChild(index)?.accessibleContext?.let {
                    readNode(
                        it,
                        "$path/$index",
                        depth + 1,
                        nodes,
                        origin,
                    )
                }
            },
        context.accessibleEditableText?.let { editable ->
            runCatching {
                editable.getTextRange(
                    0,
                    editable.charCount.coerceAtMost(100_000),
                )
            }
                .getOrNull()
        },
        context.accessibleComponent?.let { component ->
            runCatching {
                val position = component.locationOnScreen
                val size = component.size
                UiBounds(position.x - origin.x, position.y - origin.y, size.width, size.height)
            }
                .getOrNull()
        },
    )
}

private fun activeWindow(window: Window, includeNonFocusable: Boolean = true): Window =
    window.ownedWindows
        .lastOrNull {
            it.isVisible && (includeNonFocusable || it.focusableWindowState)
        }
        ?.let { activeWindow(it, includeNonFocusable) } ?: window

private fun findNode(root: AccessibleContext, path: String): AccessibleContext {
    val parts = path.split('/')
    require(parts.first() == "root") { "Invalid node path" }
    return parts.drop(1).fold(root) { context, part ->
        val index = requireNotNull(part.toIntOrNull()) { "Invalid node index" }
        require(index in 0 until context.accessibleChildrenCount) {
            "Stale node path; read /desktop/semantics again"
        }
        requireNotNull(context.getAccessibleChild(index)?.accessibleContext) {
            "Stale node path; read /desktop/semantics again"
        }
    }
}
