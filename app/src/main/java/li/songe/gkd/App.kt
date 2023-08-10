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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import li.songe.gkd.data.getAppInfo
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.isMainProcess
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider

lateinit var app: Application
var appScope = MainScope()

@HiltAndroidApp
class App : Application() {
    override fun onLowMemory() {
        super.onLowMemory()
        appScope.cancel("onLowMemory() called by system")
        appScope = MainScope()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        MMKV.initialize(this)
        LogUtils.getConfig().apply {
            isLog2FileSwitch = true
            saveDays = 7
        }
        ShizukuProvider.enableMultiProcessSupport(true)
        CrashReport.initCrashReport(applicationContext, "d0ce46b353", false)

        if (isMainProcess) {
            appScope.launch(Dispatchers.IO) {
//                提前获取 appInfo 缓存
                DbSet.subsItemDao.query().collect {
                    it.forEach { s -> s.subscriptionRaw?.apps?.forEach { app -> getAppInfo(app.id) } }
                }
            }
        }
    }
}