package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader

/** Native UI owner, used only by platform assembly, never by shared page content. */
expect class AppWindow

@Composable
expect fun GkBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)

@Composable
expect fun GkFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

@Composable
expect fun inputInsets(): Modifier
expect fun AppWindow.hideIme(): Boolean

@Composable
expect fun AppWindow.imageLoader(): ImageLoader

@Composable
expect fun AppWindow.GkSystemBars(visible: Boolean)

@Composable
expect fun AppWindow.GkAppIcon(
    appId: String,
    size: Dp,
)

expect fun AppWindow.previewActionToast(text: String, system: Boolean)
expect fun AppWindow.openExternalUrl(url: String)
