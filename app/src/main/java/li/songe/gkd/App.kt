package li.songe.gkd

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.blankj.utilcode.util.LogUtils
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.mmkv.MMKV
import li.songe.gkd.utils.Storage
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuProvider

class App : Application() {
    companion object {
        lateinit var context: Application
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        MMKV.initialize(this)
        LogUtils.d(Storage.settings)
        if (!Storage.settings.enableConsoleLogOut) {
            LogUtils.d("关闭日志控制台输出")
        }
        LogUtils.getConfig().apply {
            isLog2FileSwitch = true
            saveDays = 30
            LogUtils.getConfig().setConsoleSwitch(Storage.settings.enableConsoleLogOut)
        }
        ShizukuProvider.enableMultiProcessSupport(true)
        CrashReport.initCrashReport(applicationContext, "d0ce46b353", false)
    }
}