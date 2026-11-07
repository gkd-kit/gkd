package li.gkd.app.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.MainActivity
import li.gkd.app.settings.AppLanguage
import li.gkd.app.ui.androidState
import li.gkd.app.ui.component.GkAndroidLanguageEnvironment
import li.gkd.app.ui.navigation.GkAppNavigation
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.style.AppTheme

@Composable
fun AppRoot() {
    val activity = androidx.activity.compose.LocalActivity.current as MainActivity
    val mainVm = activity.mainVm
    val locale by AppLanguage.locale.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalUiHost provides activity,
    ) {
        GkAndroidLanguageEnvironment(locale) {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    GkAppNavigation()
                    AppOverlayHost()
                    mainVm.androidState.permissionRequests.Render(
                        modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                    )
                }
            }
        }
    }
}
