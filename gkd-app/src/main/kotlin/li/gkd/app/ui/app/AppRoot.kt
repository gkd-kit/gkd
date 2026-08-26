package li.songe.gkd.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.AppTheme

@Composable
fun AppRoot() {
    val mainVm = LocalMainViewModel.current
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MainNavigation()
            AppOverlayHost()
            mainVm.permissionRequests.Render(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
            )
        }
    }
}
