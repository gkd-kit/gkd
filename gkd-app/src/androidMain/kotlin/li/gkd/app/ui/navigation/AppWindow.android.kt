package li.gkd.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import li.gkd.app.app
import li.gkd.app.ui.image.ImageLoaders
import li.gkd.app.ui.share.optimizedImePadding
import li.gkd.app.util.AndroidStorage
import li.gkd.app.util.AndroidTarget
import li.gkd.app.ui.component.GkAppIcon as AndroidAppIcon
import li.gkd.app.ui.component.GkFullscreenDialog as AndroidFullscreenDialog

actual typealias AppWindow = li.gkd.app.MainActivity

@Composable
actual fun GkBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = BackHandler(enabled, onBack)

@Composable
actual fun AppWindow.GkAppIcon(
    appId: String,
    size: Dp,
) = AndroidAppIcon(appId = appId, size = size)

@Composable
actual fun GkFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) = AndroidFullscreenDialog(onDismiss, content)

@Composable
actual fun inputInsets(): Modifier = Modifier.optimizedImePadding()
actual fun AppWindow.hideIme(): Boolean = imeController.requestHide()

@Composable
actual fun AppWindow.imageLoader() = previewImageLoader


actual fun AppWindow.previewActionToast(text: String, system: Boolean) =
    li.gkd.app.util.ToastUtils.previewAction(text, system)

actual fun AppWindow.openExternalUrl(url: String) {
    li.gkd.app.ui.MainViewModel.requireCurrent().openUrl(url)
}

private val previewImageLoader by lazy {
    ImageLoaders.create(app, AndroidStorage.storage.coilCache) {
        if (AndroidTarget.P) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
    }
}


@Composable
actual fun AppWindow.GkSystemBars(visible: Boolean) {
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
