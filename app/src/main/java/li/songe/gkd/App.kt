package li.songe.gkd

import android.app.Application
import android.content.Context
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ProcessUtils
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import li.songe.gkd.notif.initChannel
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initStore
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.initUpgrade
import li.songe.gkd.util.isMainProcess
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider

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
        LogUtils.d("App onCreate", ProcessUtils.getCurrentProcessName())
        MMKV.initialize(this)
        LogUtils.getConfig().apply {
            isLog2FileSwitch = true
            saveDays = 7
        }
        ShizukuProvider.enableMultiProcessSupport(true)
        CrashReport.initCrashReport(applicationContext, "d0ce46b353", false)

        if (isMainProcess) {
            initChannel()
            initStore()
            initAppState()
            initSubsState()
            initUpgrade()
        }
    }
}