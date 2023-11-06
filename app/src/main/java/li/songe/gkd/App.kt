package li.songe.gkd

import android.app.Application
import android.content.Context
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import li.songe.gkd.debug.clearHttpSubs
import li.songe.gkd.notif.initChannel
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initStore
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.launchTry
import org.lsposed.hiddenapibypass.HiddenApiBypass

val appScope by lazy { MainScope() }

private lateinit var _app: Application
val app: Application
    get() = _app


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
        _app = this

        CrashReport.initCrashReport(applicationContext, "d0ce46b353", false)
        MMKV.initialize(this)
        LogUtils.getConfig().apply {
            setConsoleSwitch(BuildConfig.DEBUG)
            saveDays = 7
        }

        appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            clearHttpSubs()
        }
    }
}