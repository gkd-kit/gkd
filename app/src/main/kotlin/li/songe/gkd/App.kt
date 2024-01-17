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
import li.songe.gkd.data.DeviceInfo
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

        @Suppress("SENSELESS_COMPARISON") if (BuildConfig.GKD_BUGLY_APP_ID != null) {
            CrashReport.setDeviceModel(this, DeviceInfo.instance.model)
            CrashReport.setIsDevelopmentDevice(this, BuildConfig.DEBUG)
            CrashReport.initCrashReport(applicationContext,
                BuildConfig.GKD_BUGLY_APP_ID,
                BuildConfig.DEBUG,
                CrashReport.UserStrategy(this).apply {
                    setCrashHandleCallback(object : CrashReport.CrashHandleCallback() {
                        override fun onCrashHandleStart(
                            p0: Int,
                            p1: String?,
                            p2: String?,
                            p3: String?,
                        ): MutableMap<String, String> {
                            LogUtils.d(p0, p1, p2, p3) // 将报错日志输出到本地
                            return super.onCrashHandleStart(p0, p1, p2, p3)
                        }
                    })
                })
        }

        MMKV.initialize(this)
        LogUtils.getConfig().apply {
            setConsoleSwitch(BuildConfig.DEBUG)
            saveDays = 7
        }
        LogUtils.d("GIT_COMMIT_URL: $GIT_COMMIT_URL")

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