package li.songe.gkd

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.debug.clearHttpSubs
import li.songe.gkd.notif.initChannel
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.util.GIT_COMMIT_URL
import li.songe.gkd.util.initAppState
import li.songe.gkd.util.initFolder
import li.songe.gkd.util.initStore
import li.songe.gkd.util.initSubsState
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.setReactiveToastStyle
import org.lsposed.hiddenapibypass.HiddenApiBypass


val appScope by lazy { MainScope() }

private lateinit var innerApp: Application
val app: Application
    get() = innerApp

val applicationInfo by lazy {
    app.packageManager.getApplicationInfo(
        app.packageName,
        PackageManager.GET_META_DATA
    )
}

val channel by lazy { applicationInfo.metaData.getString("channel") }

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

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
            setConsoleSwitch(BuildConfig.DEBUG)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "GIT_COMMIT_URL: $GIT_COMMIT_URL",
            "VERSION_CODE: ${BuildConfig.VERSION_CODE}",
            "VERSION_NAME: ${BuildConfig.VERSION_NAME}",
            "CHANNEL: $channel"
        )
        initFolder()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                LogUtils.d("onActivityCreated", activity, savedInstanceState)
            }

            override fun onActivityStarted(activity: Activity) {
                LogUtils.d("onActivityStarted", activity)
            }

            override fun onActivityResumed(activity: Activity) {
                LogUtils.d("onActivityResumed", activity)
            }

            override fun onActivityPaused(activity: Activity) {
                LogUtils.d("onActivityPaused", activity)
            }

            override fun onActivityStopped(activity: Activity) {
                LogUtils.d("onActivityStopped", activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                LogUtils.d("onActivitySaveInstanceState", activity, outState)
            }

            override fun onActivityDestroyed(activity: Activity) {
                LogUtils.d("onActivityDestroyed", activity)
            }
        })
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
        appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            clearHttpSubs()
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
    val name = ComponentName(app, GkdAbService::class.java)
    while (colonSplitter.hasNext()) {
        if (ComponentName.unflattenFromString(colonSplitter.next()) == name) {
            return true
        }
    }
    return false
}
