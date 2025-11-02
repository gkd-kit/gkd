package li.songe.gkd.ui.component

import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import li.songe.gkd.ui.share.LocalDarkTheme

@Composable
fun FullscreenDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) = Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
        windowTitle = "全局弹窗",
    )
) {
    val activity = LocalActivity.current!!
    val parentView = LocalView.current.parent as View
    val dialogWindow = (parentView as DialogWindowProvider).window
    SideEffect {
        dialogWindow.setDimAmount(0f)
        dialogWindow.attributes = WindowManager.LayoutParams().apply {
            copyFrom(activity.window.attributes)
            type = dialogWindow.attributes.type
            windowAnimations = android.R.style.Animation_Dialog
        }
        parentView.layoutParams = FrameLayout.LayoutParams(
            activity.window.decorView.width,
            activity.window.decorView.height
        )
        parentView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
    val darkTheme = LocalDarkTheme.current
    val controller = remember(dialogWindow) {
        WindowInsetsControllerCompat(
            dialogWindow,
            dialogWindow.decorView
        )
    }
    LaunchedEffect(darkTheme) {
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
    content()
}
