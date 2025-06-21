package li.songe.gkd

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.data.selfAppInfo
import li.songe.gkd.debug.clearHttpSubs
import li.songe.gkd.notif.initChannel
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.service.A11yService
import li.songe.gkd.shizuku.initShizuku
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initStore
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.setReactiveToastStyle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku


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

val activityManager by lazy { app.getSystemService(ACTIVITY_SERVICE) as ActivityManager }
val appOpsManager by lazy { app.getSystemService(AppOpsManager::class.java) as AppOpsManager }

data class AppMeta(
    val channel: String = applicationInfo.metaData.getString("channel")!!,
    val commitId: String = applicationInfo.metaData.getString("commitId")!!,
    val commitTime: Long = applicationInfo.metaData.getString("commitTime")!!.toLong(),
    val tagName: String? = applicationInfo.metaData.getString("tagName")!!.takeIf { it.isNotEmpty() },
    val commitUrl: String = "https://github.com/gkd-kit/gkd/" + if (tagName != null) "tree/$tagName" else "commit/$commitId",
    val updateEnabled: Boolean = applicationInfo.metaData.getBoolean("updateEnabled"),
    val debuggable: Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    val versionCode: Int = selfAppInfo.versionCode.toInt(),
    val versionName: String = selfAppInfo.versionName!!,
    val appId: String = app.packageName!!,
    val appName: String = app.getString(SafeR.app_name)
)

val META by lazy { AppMeta() }

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    val startTime = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        innerApp = this
        Utils.init(this)

        val errorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LogUtils.d("UncaughtExceptionHandler", t, e)
            errorHandler?.uncaughtException(t, e)
        }
        MMKV.initialize(this)

        Toaster.init(this)
        setReactiveToastStyle()

        LogUtils.getConfig().apply {
            setConsoleSwitch(META.debuggable)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "META",
            META,
        )
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    a11yServiceEnabledFlow.value = getA11yServiceEnabled()
                }
            }
        )
        Shizuku.addBinderReceivedListener {
            appScope.launchTry(Dispatchers.IO) {
                shizukuOkState.updateAndGet()
            }
        }
        Shizuku.addBinderDeadListener {
            shizukuOkState.stateFlow.value = false
        }
        appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            initShizuku()
            clearHttpSubs()
            syncFixState()
        }
    }
}

val a11yServiceEnabledFlow by lazy { MutableStateFlow(getA11yServiceEnabled()) }
private fun getA11yServiceEnabled(): Boolean {
    val value = try {
        Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    }
    if (value.isNullOrEmpty()) return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(value)
    while (colonSplitter.hasNext()) {
        if (ComponentName.unflattenFromString(colonSplitter.next()) == A11yService.a11yComponentName) {
            return true
        }
    }
    return false
}
