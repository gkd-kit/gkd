package li.songe.gkd

import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.StartActivityLauncher
import li.songe.gkd.composition.CompositionActivity
import li.songe.gkd.composition.Hook.useLifeCycleLog
import li.songe.gkd.ui.home.HomePage
import li.songe.gkd.ui.theme.MainTheme
import li.songe.gkd.util.Ext.LocalLauncher
import li.songe.gkd.util.Storage
import li.songe.gkd.util.UseHook
import li.songe.router.RouterHost


class MainActivity : CompositionActivity({
    useLifeCycleLog()

    val launcher = StartActivityLauncher(this)
    onFinish { fs ->
        LogUtils.d(Storage.settings)
        if (Storage.settings.excludeFromRecents) {
            finishAndRemoveTask() // 会让miui桌面回退动画失效
        } else {
            fs()
        }
    }
    onConfigurationChanged { newConfig ->
        LogUtils.d(newConfig)
        UseHook.update(newConfig)
    }

    setContent {
        BackHandler {
            finish()
        }
        CompositionLocalProvider(LocalLauncher provides launcher) {
            MainTheme(false) {
                RouterHost(HomePage)
            }
        }
    }




})



