package li.songe.gkd

import android.app.Application
import android.content.Context
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import li.songe.gkd.debug.clearHttpSubs
import li.songe.gkd.notif.initChannel
import li.songe.gkd.util.GIT_COMMIT_URL
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.initStore
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.launchTry
import org.lsposed.hiddenapibypass.HiddenApiBypass


val appScope by lazy { MainScope() }

private lateinit var innerApp: Application
val app: Application
    get() = innerApp


@HiltAndroidApp
class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        innerApp = this

        val errorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LogUtils.d("UncaughtExceptionHandler", t, e)
            errorHandler?.uncaughtException(t, e)
        }
        Toaster.init(this)
        MMKV.initialize(this)
        LogUtils.getConfig().apply {
            setConsoleSwitch(BuildConfig.DEBUG)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "GIT_COMMIT_URL: $GIT_COMMIT_URL",
            "VERSION_CODE: ${BuildConfig.VERSION_CODE}",
            "VERSION_NAME: ${BuildConfig.VERSION_NAME}",
        )

        initFolder()
        appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            clearHttpSubs()
        }
    }
}