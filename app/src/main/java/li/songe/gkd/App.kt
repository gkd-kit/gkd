package li.songe.gkd

import android.app.Application
import com.blankj.utilcode.util.LogUtils
import com.tencent.bugly.crashreport.CrashReport

class App:Application() {
    companion object{
        lateinit var context:Application
    }
    override fun onCreate() {
        super.onCreate()
        context = this
        LogUtils.d("onCreate")
        LogUtils.getConfig().isLog2FileSwitch = true
        CrashReport.initCrashReport(applicationContext, "d0ce46b353", false);
    }
}