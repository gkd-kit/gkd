package li.songe.gkd

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.ramcosta.composedestinations.DestinationsNavHost
import dagger.hilt.android.AndroidEntryPoint
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.ui.NavGraphs
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.storeFlow

@AndroidEntryPoint
class MainActivity : CompositionActivity({
    useLifeCycleLog()

    val launcher = StartActivityLauncher(this)
    onFinish { fs ->
        if (storeFlow.value.excludeFromRecents) {
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

//    lifecycleScope.launchTry(IO) {
//        File("/sdcard/Android/data/${packageName}/files/snapshot").walk().maxDepth(1)
//            .filter { it.isDirectory && !it.name.endsWith("snapshot") }.forEach { folder ->
//                val snapshot = Singleton.json.decodeFromString<Snapshot>(File(folder.absolutePath + "/${folder.name}.json").readText())
//                try {
//                    DbSet.snapshotDao.insert(snapshot)
//                }catch (e:Exception){
//                    e.printStackTrace()
//                    LogUtils.d("insert failed, ${snapshot.id}")
//                    return@launchTry
//                }
//            }
//    }

    setContent {
        val navController = rememberNavController()
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




