package li.gkd.app.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import li.gkd.app.ui.androidState
import li.gkd.app.ui.navigation.GkAppNavigation
import li.gkd.app.ui.style.AppTheme

@Composable
fun AppRoot() {
    val activity = androidx.activity.compose.LocalActivity.current as li.gkd.app.MainActivity
    val mainVm = activity.mainVm
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            GkAppNavigation(host = activity)
            AppOverlayHost()
            mainVm.androidState.permissionRequests.Render(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )
        }
    }
}
