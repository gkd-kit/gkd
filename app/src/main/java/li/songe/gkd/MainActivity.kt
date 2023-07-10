package li.songe.gkd

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.rememberNavController
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.utils.LocalLauncher
import li.songe.gkd.utils.LocalNavController
import li.songe.gkd.utils.StackCacheProvider
import li.songe.gkd.utils.Storage


class MainActivity : CompositionActivity({
    useLifeCycleLog()

    val launcher = StartActivityLauncher(this)
    onFinish { fs ->
        if (Storage.settings.excludeFromRecents) {
            finishAndRemoveTask() // 会让miui桌面回退动画失效
        } else {
            fs()
        }
    }

//    https://juejin.cn/post/7169147194400833572
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
// TextView[a==1||b==1||a==1||(a==1&&b==true)]
//    lifecycleScope.launchTry {
//        delay(1000)
//        WindowCompat.setDecorFitsSystemWindows(window, false)
//        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
//        insetsController.hide(WindowInsetsCompat.Type.statusBars())
//    }

//    var shizukuIsOK = false
//    val receivedListener: () -> Unit = {
//        shizukuIsOK = true
//    }
//    Shizuku.addBinderReceivedListenerSticky(receivedListener)
//    onDestroy {
//        Shizuku.removeBinderReceivedListener(receivedListener)
//    }
//    lifecycleScope.launchWhile {
//        if (shizukuIsOK) {
//            val top = activityTaskManager.getTasks(1, false, true)?.firstOrNull()
//            if (top!=null) {
//                LogUtils.d(top.topActivity?.packageName, top.topActivity?.className, top.topActivity?.shortClassName)
//            }
//        }
//        delay(5000)
//    }


    setContent {
        val navController = rememberNavController()
        AppTheme(false) {
            CompositionLocalProvider(
                LocalLauncher provides launcher,
                LocalNavController provides navController
            ) {
                StackCacheProvider(navController = navController) {
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        navController = navController,
                    )
                }
            }
        }
    }
})




