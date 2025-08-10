package li.songe.gkd

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import kotlinx.coroutines.MainScope
import kotlinx.serialization.Serializable
import li.songe.gkd.data.selfAppInfo
import li.songe.gkd.notif.initChannel
import li.songe.gkd.service.clearHttpSubs
import li.songe.gkd.shizuku.initShizuku
import li.songe.gkd.store.initStore
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.initToast
import li.songe.gkd.util.toJson5String
import org.lsposed.hiddenapibypass.HiddenApiBypass


val appScope by lazy { MainScope() }

private lateinit var innerApp: App
val app: App
    get() = innerApp

private val applicationInfo by lazy {
    app.packageManager.getApplicationInfo(
        app.packageName,
        PackageManager.GET_META_DATA
    )
}

private fun getMetaString(key: String): String {
    return applicationInfo.metaData.getString(key) ?: error("Missing meta-data: $key")
}

@Serializable
data class AppMeta(
    val channel: String = getMetaString("channel"),
    val commitId: String = getMetaString("commitId"),
    val commitTime: Long = getMetaString("commitTime").toLong(),
    val tagName: String? = getMetaString("tagName").takeIf { it.isNotEmpty() },
    val debuggable: Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    val versionCode: Int = selfAppInfo.versionCode,
    val versionName: String = selfAppInfo.versionName!!,
    val appId: String = app.packageName!!,
    val appName: String = app.getString(SafeR.app_name)
) {
    val commitUrl = "https://github.com/gkd-kit/gkd/".run {
        plus(if (tagName != null) "tree/$tagName" else "commit/$commitId")
    }
    val isGkdChannel = channel == "gkd"
    val updateEnabled: Boolean
        get() = isGkdChannel
}

val META by lazy { AppMeta() }

class App : Application() {
    init {
        innerApp = this
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    val startTime = System.currentTimeMillis()

    val activityManager by lazy { app.getSystemService(ACTIVITY_SERVICE) as ActivityManager }
    val appOpsManager by lazy { app.getSystemService(APP_OPS_SERVICE) as AppOpsManager }

    override fun onCreate() {
        super.onCreate()
        Utils.init(this)
        LogUtils.getConfig().apply {
            setConsoleSwitch(META.debuggable)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "META",
            toJson5String(META),
        )
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LogUtils.d("UncaughtExceptionHandler", t, e)
        }
        initToast()
        initStore()
        initChannel()
        initAppState()
        initShizuku()
        initSubsState()
        clearHttpSubs()
        syncFixState()
    }
}
