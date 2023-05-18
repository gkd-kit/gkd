package li.songe.gkd

import android.app.Application
import com.blankj.utilcode.util.LogUtils
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.mmkv.MMKV
import li.songe.gkd.util.Storage

class App : Application() {
    companion object {
        lateinit var context: Application
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        MMKV.initialize(this)
        LogUtils.d(Storage.settings)
        if (!Storage.settings.enableConsoleLogOut){
            LogUtils.d("关闭日志控制台输出")
        }
        LogUtils.getConfig().apply {
            isLog2FileSwitch = true
            saveDays = 30
            LogUtils.getConfig().setConsoleSwitch(Storage.settings.enableConsoleLogOut)
        }
        CrashReport.initCrashReport(applicationContext, "d0ce46b353", false)
    }
}