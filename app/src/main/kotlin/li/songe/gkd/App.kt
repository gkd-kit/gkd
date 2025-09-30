package li.songe.gkd

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import kotlinx.coroutines.MainScope
import kotlinx.serialization.Serializable
import li.songe.gkd.data.selfAppInfo
import li.songe.gkd.notif.initChannel
import li.songe.gkd.service.clearHttpSubs
import li.songe.gkd.service.initA11yWhiteAppList
import li.songe.gkd.shizuku.initShizuku
import li.songe.gkd.store.initStore
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.PKG_FLAGS
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.initToast
import li.songe.gkd.util.toJson5String
import li.songe.gkd.util.toast
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

// https://github.com/android-cs/16/blob/main/packages/SettingsLib/src/com/android/settingslib/accessibility/AccessibilityUtils.java#L41
private const val ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR = ':'

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
    val isGkdChannel get() = channel == "gkd"
    val updateEnabled get() = isGkdChannel
    val isBeta get() = versionName.contains("beta")
}

val META by lazy { AppMeta() }

class App : Application() {
    init {
        innerApp = this
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (AndroidTarget.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    fun getSecureString(name: String): String? = Settings.Secure.getString(contentResolver, name)
    fun putSecureString(name: String, value: String?): Boolean {
        return Settings.Secure.putString(contentResolver, name, value)
    }

    fun putSecureInt(name: String, value: Int): Boolean {
        return Settings.Secure.putInt(contentResolver, name, value)
    }

    fun getSecureA11yServices(): MutableSet<ComponentName> {
        val value = getSecureString(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (value.isNullOrEmpty()) return mutableSetOf()
        return value.split(
            ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR
        ).mapNotNull { ComponentName.unflattenFromString(it) }.toHashSet()
    }

    fun putSecureA11yServices(services: Set<ComponentName>) {
        putSecureString(
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            services.joinToString(ENABLED_ACCESSIBILITY_SERVICES_SEPARATOR.toString()) { it.flattenToShortString() }
        )
    }

    fun resolveAppId(intent: Intent): String? {
        return intent.resolveActivity(packageManager)?.packageName
    }

    fun getPkgInfo(appId: String): PackageInfo? = try {
        packageManager.getPackageInfo(appId, PKG_FLAGS)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun resolveAppId(action: String, category: String? = null): String? {
        val intent = Intent(action)
        if (category != null) {
            intent.addCategory(category)
        }
        return resolveAppId(intent)
    }

    val startTime = System.currentTimeMillis()
    var justStarted: Boolean = true
        get() {
            if (field) {
                field = System.currentTimeMillis() - startTime < 3_000
            }
            return field
        }

    val activityManager by lazy { app.getSystemService(ACTIVITY_SERVICE) as ActivityManager }
    val appOpsManager by lazy { app.getSystemService(APP_OPS_SERVICE) as AppOpsManager }
    val inputMethodManager by lazy { app.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }
    val windowManager by lazy { app.getSystemService(WINDOW_SERVICE) as WindowManager }
    val keyguardManager by lazy { app.getSystemService(KEYGUARD_SERVICE) as KeyguardManager }
    val clipboardManager by lazy { app.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager }
    val powerManager by lazy { getSystemService(POWER_SERVICE) as PowerManager }

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
            toast(e.message ?: e.toString())
            LogUtils.d("UncaughtExceptionHandler", t, e)
        }
        initToast()
        initStore()
        initChannel()
        initAppState()
        initShizuku()
        initSubsState()
        initA11yWhiteAppList()
        clearHttpSubs()
        syncFixState()
    }
}
