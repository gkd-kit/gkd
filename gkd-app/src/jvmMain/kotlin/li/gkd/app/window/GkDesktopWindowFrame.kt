package li.gkd.app.window

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.icon.Logo
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import java.awt.Color as JavaColor

/** Geometry shared by live screenshots and pointer input, in AWT logical coordinates. */
object DesktopWindowGeometry {
    private const val CONTENT = "gkd.window.contentBounds"
    private const val FRAME = "gkd.window.customFrame"
    fun contentBounds(window: Window, frame: Boolean = false): Rectangle {
        val host = window as? RootPaneContainer
        val pane = host?.contentPane ?: window
        val full = Rectangle(pane.locationOnScreen, pane.size)
        val content = host?.rootPane?.getClientProperty(CONTENT) as? Rectangle
        return if (frame || content == null) full else Rectangle(
            full.x + content.x, full.y + content.y, content.width, content.height,
        )
    }

    fun isCustom(window: ComposeWindow) = window.rootPane.getClientProperty(FRAME) == true
    fun update(window: ComposeWindow, bounds: Rectangle, custom: Boolean) {
        window.rootPane.putClientProperty(CONTENT, bounds)
        window.rootPane.putClientProperty(FRAME, custom)
    }

    fun clear(window: ComposeWindow) {
        window.rootPane.putClientProperty(CONTENT, null)
        window.rootPane.putClientProperty(FRAME, null)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GkDesktopWindowFrame(
    window: ComposeWindow,
    state: WindowState,
    title: String,
    dark: Boolean,
    onCloseRequest: () -> Unit,
    viewportSize: DpSize? = null,
    onReady: (() -> Unit)? = null,
    onOpenControls: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var controller by remember(window) { mutableStateOf<WindowsWindowFrameController?>(null) }
    var frameInitialized by remember(window) { mutableStateOf(false) }
    var active by remember(window) { mutableStateOf(window.isActive) }
    val currentReadyCallback by rememberUpdatedState(onReady)
    val background = MaterialTheme.colorScheme.background
    SideEffect {
        val color = JavaColor(background.toArgb(), true)
        window.background = color
        window.contentPane.background = color
    }
    val density = LocalDensity.current.density
    DisposableEffect(window) {
        val listener = object : WindowAdapter() {
            override fun windowActivated(e: WindowEvent) {
                active = true
            }

            override fun windowDeactivated(e: WindowEvent) {
                active = false
            }
        }
        window.addWindowListener(listener)
        val installed = if (System.getProperty("os.name").startsWith("Windows")) {
            runCatching { WindowsWindowFrameController(window).also { it.install() } }
                .onFailure { System.err.println("GKD: using system title bar: $it") }.getOrNull()
        } else null
        controller = installed
        frameInitialized = true
        onDispose {
            window.removeWindowListener(listener)
            installed?.close()
            DesktopWindowGeometry.clear(window)
        }
    }
    val showTitle = controller != null && state.placement != WindowPlacement.Fullscreen
    DisposableEffect(window, frameInitialized, viewportSize, showTitle, density) {
        var disposed = false
        if (frameInitialized && (currentReadyCallback != null || viewportSize != null)) {
            // A Compose coroutine can run inside render; defer to a separate AWT event to avoid re-entry.
            SwingUtilities.invokeLater {
                if (!disposed && window.isDisplayable) {
                    viewportSize?.let { viewport ->
                        val insets = window.insets
                        val target = DpSize(
                            (viewport.width.value + insets.left + insets.right).dp,
                            (viewport.height.value + insets.top + insets.bottom + if (showTitle) 32 else 0).dp,
                        )
                        if (window.isVisible) {
                            // Let Compose own subsequent resizes and reconcile native resize events.
                            state.size = target
                        } else {
                            // Initial geometry must be applied synchronously before the first visible frame.
                            window.setSize(
                                target.width.value.roundToInt(),
                                target.height.value.roundToInt()
                            )
                        }
                    }
                    if (!window.isVisible) {
                        window.validate()
                        window.renderImmediately()
                        currentReadyCallback?.invoke()
                    }
                }
            }
        }
        onDispose { disposed = true }
    }
    SideEffect {
        if (!showTitle) controller?.layout = WindowsWindowHitTest()
    }
    Column(Modifier.fillMaxSize().background(background)) {
        if (showTitle) {
            // Consume the host theme's animated colors so the caption and content share one transition.
            val foreground = MaterialTheme.colorScheme.onSurface
            Row(
                Modifier.fillMaxWidth().height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .onGloballyPositioned { coordinates ->
                        controller?.let {
                            it.layout = it.layout.copy(title = coordinates.boundsInWindow())
                        }
                    }, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    GkIcons.Logo,
                    null,
                    Modifier.padding(start = 10.dp).size(16.dp),
                    tint = Color.Unspecified
                )
                Text(
                    title, color = foreground.copy(alpha = if (active) 1f else .55f),
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                if (onOpenControls != null) {
                    CaptionButton(
                        "打开模拟控制", GkIcons.Settings, foreground, dark, active,
                        { rect -> controller?.let { it.layout = it.layout.copy(settings = rect) } },
                        iconSize = 16.dp, onClick = onOpenControls
                    )
                }
                CaptionButton(
                    "最小化窗口", CaptionIcons.minimize, foreground, dark, active,
                    { rect -> controller?.let { it.layout = it.layout.copy(minimize = rect) } },
                    onClick = { state.isMinimized = true })
                CaptionButton(
                    if (state.placement == WindowPlacement.Maximized) "还原窗口" else "最大化窗口",
                    if (state.placement == WindowPlacement.Maximized) CaptionIcons.restore else CaptionIcons.maximize,
                    foreground, dark, active,
                    { rect -> controller?.let { it.layout = it.layout.copy(maximize = rect) } },
                    onClick = {
                        state.placement = if (state.placement == WindowPlacement.Maximized)
                            WindowPlacement.Floating else WindowPlacement.Maximized
                    })
                CaptionButton(
                    "关闭窗口", CaptionIcons.close, foreground, dark, active,
                    { rect -> controller?.let { it.layout = it.layout.copy(close = rect) } },
                    close = true, onClick = onCloseRequest
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
            val rect = it.boundsInWindow()
            DesktopWindowGeometry.update(
                window, Rectangle(
                    (rect.left / density).roundToInt(), (rect.top / density).roundToInt(),
                    (rect.width / density).roundToInt(), (rect.height / density).roundToInt(),
                ), showTitle
            )
        }) { content() }
    }
}

@Composable
private fun CaptionButton(
    label: String,
    icon: ImageVector,
    foreground: Color,
    dark: Boolean,
    active: Boolean,
    bounds: (Rect) -> Unit,
    close: Boolean = false,
    iconSize: Dp = 10.dp,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val background = when {
        close && pressed -> Color(0xffc42b1c)
        close && hovered -> Color(0xffe81123)
        pressed -> foreground.copy(alpha = .16f)
        hovered -> foreground.copy(alpha = if (dark) .10f else .08f)
        else -> Color.Transparent
    }
    Box(
        Modifier.size(46.dp, 32.dp).background(background)
            .onGloballyPositioned { bounds(it.boundsInWindow()) }
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center) {
        Icon(
            icon,
            label,
            Modifier.size(iconSize),
            tint = if (close && (hovered || pressed)) Color.White
            else foreground.copy(alpha = if (active) 1f else .55f)
        )
    }
}

private object CaptionIcons {
    val minimize = ImageVector.Builder("WindowMinimize", 10.dp, 10.dp, 10f, 10f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1f) {
            moveTo(0f, 5.5f); lineTo(
            10f,
            5.5f
        )
        }
    }.build()
    val maximize = ImageVector.Builder("WindowMaximize", 10.dp, 10.dp, 10f, 10f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1f) {
            moveTo(.5f, .5f); lineTo(9.5f, .5f); lineTo(9.5f, 9.5f); lineTo(.5f, 9.5f); close()
        }
    }.build()
    val restore = ImageVector.Builder("WindowRestore", 10.dp, 10.dp, 10f, 10f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1f) {
            moveTo(2.5f, 2.5f); lineTo(2.5f, .5f); lineTo(9.5f, .5f); lineTo(9.5f, 7.5f); lineTo(
            7.5f,
            7.5f
        )
            moveTo(.5f, 2.5f); lineTo(7.5f, 2.5f); lineTo(7.5f, 9.5f); lineTo(.5f, 9.5f); close()
        }
    }.build()
    val close = ImageVector.Builder("WindowClose", 10.dp, 10.dp, 10f, 10f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 1f) {
            moveTo(.5f, .5f); lineTo(9.5f, 9.5f); moveTo(9.5f, .5f); lineTo(.5f, 9.5f)
        }
    }.build()
}
