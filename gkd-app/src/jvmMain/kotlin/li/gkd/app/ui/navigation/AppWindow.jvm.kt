package li.gkd.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import li.gkd.app.GkDesktopBackHandler
import li.gkd.app.ui.component.GkAppIcon
import li.gkd.app.ui.component.GkDialog

actual typealias AppWindow = li.gkd.app.DesktopSession

@Composable
actual fun GkBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    if (enabled) GkDesktopBackHandler(onBack = onBack)
}

@Composable
actual fun AppWindow.GkAppIcon(
    appId: String,
    size: Dp,
) = GkAppIcon(runtime.appIcon(appId), size = size)

@Composable
actual fun GkFullscreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    GkDesktopBackHandler(onBack = onDismiss)
    GkDialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
        ) { content() }
    }
}

@Composable
actual fun inputInsets(): Modifier = Modifier
actual fun AppWindow.hideIme(): Boolean = this.state.dismissIme()

@Composable
actual fun AppWindow.imageLoader() = runtime.imageLoader

@Composable
actual fun AppWindow.GkSystemBars(visible: Boolean) {
    val state = this.state
    val old = remember { state.environment.android.statusBarVisible }
    DisposableEffect(Unit) {
        onDispose {
            state.simulatorCommand {
                state.simulator.updateAndroid {
                    it.copy(
                        statusBarVisible = old
                    )
                }
            }
        }
    }
    LaunchedEffect(visible) {
        state.simulatorCommand {
            state.simulator.updateAndroid {
                it.copy(
                    statusBarVisible = visible
                )
            }
        }
    }
}

actual fun AppWindow.previewActionToast(text: String, system: Boolean) {
    state.toast.show(text)
}

actual fun AppWindow.openExternalUrl(url: String) {
    li.gkd.app.ui.platform.SystemActionFeedback.openExternal(url, state.toast::show)
}
