package li.songe.gkd

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.RequestPermissionLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import dagger.hilt.android.AndroidEntryPoint
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.LocalPickContentLauncher
import li.songe.gkd.util.LocalRequestPermissionLauncher
import li.songe.gkd.util.UpgradeDialog

@AndroidEntryPoint
class MainActivity : CompositionActivity({
    installSplashScreen()
    useLifeCycleLog()

    val launcher = StartActivityLauncher(this)
    val pickContentLauncher = PickContentLauncher(this)
    val requestPermissionLauncher = RequestPermissionLauncher(this)

    //    https://juejin.cn/post/7169147194400833572
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//        window.attributes.layoutInDisplayCutoutMode =
//            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
//    }

    setContent {
        UpgradeDialog()
        val navController = rememberNavController()
        AppTheme(false) {
            CompositionLocalProvider(
                LocalLauncher provides launcher,
                LocalPickContentLauncher provides pickContentLauncher,
                LocalRequestPermissionLauncher provides requestPermissionLauncher,
                LocalNavController provides navController
            ) {
                DestinationsNavHost(
                    navGraph = NavGraphs.root, navController = navController, modifier = Modifier
                )
            }
        }
    }
})




