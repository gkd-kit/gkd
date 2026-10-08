package li.gkd.app.ui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import li.gkd.app.ui.component.GkFullscreenDialog as AndroidFullscreenDialog
import li.gkd.app.ui.share.optimizedImePadding

actual typealias UiHost = li.gkd.app.MainActivity

@Composable
actual fun GkBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = BackHandler(enabled, onBack)

@Composable
actual fun GkFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) = AndroidFullscreenDialog(onDismiss, content)

@Composable
actual fun inputInsets(): Modifier = Modifier.optimizedImePadding()
actual fun UiHost.hideIme(): Boolean = imeController.requestHide()

@Composable
actual fun UiHost.GkSystemBars(visible: Boolean) {
    val context = this
    val controller = remember {
        WindowCompat.getInsetsController(context.window, context.window.decorView)
    }
    DisposableEffect(null) {
        val oldBehavior = controller.systemBarsBehavior
        val oldLight = controller.isAppearanceLightStatusBars
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        onDispose {
            controller.systemBarsBehavior = oldBehavior
            controller.isAppearanceLightStatusBars = oldLight
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

}
