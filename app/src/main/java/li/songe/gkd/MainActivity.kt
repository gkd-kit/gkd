package li.songe.gkd

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.StartActivityLauncher
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.router.RouterHost
import li.songe.gkd.store.Storage
import li.songe.gkd.ui.home.HomePage
import li.songe.gkd.ui.theme.MainTheme
import li.songe.gkd.use.UseHook
import java.util.*


class MainActivity : CompositionActivity() {

    val launcher by lazy { StartActivityLauncher(this) }
    val barcodeEncoder by lazy { BarcodeEncoder() }

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//      launcher  必须在 onStart 及其被使用之前初始化
        launcher
//        lifecycleScope.launchWhenCreated {
//            while (true) {
//                delay(3000)
//                localeString = LocaleString(Locale.SIMPLIFIED_CHINESE)
//                delay(3000)
//                localeString = LocaleString(Locale.ENGLISH)
//            }
//        }

//        WindowCompat.setDecorFitsSystemWindows(window, false)


        lifecycleScope.launch {
//            动画不生效, 需要延迟渲染, 目前发现小米手机是这样
            delay(100)
            setContent {
                stringResource(R.string.app_name)
                MainTheme(false) {
                    RouterHost(startPage = HomePage)
                }
            }
        }
//        Shizuku.addRequestPermissionResultListener(this::onRequestPermissionsResult)
//        NetworkUtils
//        lifecycleScope.launchWhenCreated {
//            delay(1000)
//            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
//            val tasks = am.appTasks
//            tasks?.forEach {
//                it.setExcludeFromRecents(Storage.settings.excludeFromRecents)
//            }
//        }
        if (Calendar.getInstance()[Calendar.DAY_OF_WEEK] == Calendar.TUESDAY) {
            LogUtils.d("KFC v50$")
        }

    }





//    override fun onDestroy() {
//        super.onDestroy()
////        Shizuku.removeRequestPermissionResultListener(this::onRequestPermissionsResult)
//    }

//    private fun checkPermission(code: Int): Boolean {
//        if (Shizuku.isPreV11()) {
//            // Pre-v11 is unsupported
//            return false
//        }
//        return when {
//            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
//                // Granted
//                true
//            }
//            Shizuku.shouldShowRequestPermissionRationale() -> {
//                // Users choose "Deny and don't ask again"
//                false
//            }
//            else -> {
//                // Request the permission
//                Shizuku.requestPermission(code)
//                false
//            }
//        }
//    }

    override fun finish() {
        if (Storage.settings.excludeFromRecents) {
//        https://stackoverflow.com/questions/13385289
//            会让miui桌面回退动画失效
            finishAndRemoveTask()
        } else {
            super.finish()
        }
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LogUtils.d(newConfig)
        UseHook.update(newConfig)

    }


}
