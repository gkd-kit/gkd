package li.songe.gkd

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import dagger.hilt.android.AndroidEntryPoint
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.UpgradeDialog
import li.songe.gkd.util.storeFlow

@AndroidEntryPoint
class MainActivity : CompositionActivity({
    installSplashScreen()
    useLifeCycleLog()

    val launcher = StartActivityLauncher(this)
    onFinish { fs ->
        if (storeFlow.value.excludeFromRecents) {
            finishAndRemoveTask() // 会让miui桌面回退动画失效
            LogUtils.d("finishAndRemoveTask")
        } else {
            fs()
        }
    }

    //    https://juejin.cn/post/7169147194400833572
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    setContent {
        UpgradeDialog()
        val navController = rememberNavController()
        BackHandler {
            if (navController.currentBackStack.value.size <= 2) {
                finish()
            } else {
                navController.popBackStack()
            }
        }
        AppTheme(false) {
            CompositionLocalProvider(
                LocalLauncher provides launcher, LocalNavController provides navController
            ) {
                DestinationsNavHost(
                    navGraph = NavGraphs.root, navController = navController, modifier = Modifier
                )
            }
        }
    }
})




