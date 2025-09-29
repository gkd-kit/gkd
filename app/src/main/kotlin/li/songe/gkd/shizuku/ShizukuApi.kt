package li.songe.gkd.shizuku


import android.content.ComponentName
import android.content.pm.PackageManager
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.MutexState
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

inline fun <T> safeInvokeMethod(
    block: () -> T
): T? = try {
    block()
} catch (e: Throwable) {
    e.printStackTrace()
    null
}

fun getStubService(name: String, condition: Boolean): ShizukuBinderWrapper? {
    if (!condition) return null
    val service = SystemServiceHelper.getSystemService(name) ?: return null
    return ShizukuBinderWrapper(service)
}

private val shizukuUsedFlow by lazy {
    combine(
        shizukuOkState.stateFlow,
        storeFlow.map { it.enableShizuku },
    ) { a, b ->
        a && b
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

class ShizukuContext(
    val serviceWrapper: UserServiceWrapper?,
    val packageManager: SafePackageManager?,
    val userManager: SafeUserManager?,
    val activityManager: SafeActivityManager?,
    val activityTaskManager: SafeActivityTaskManager?,
    val appOpsService: SafeAppOpsService?,
    val inputManager: SafeInputManager?,
) {
    init {
        if (activityTaskManager != null) {
            activityTaskManager.registerDefault()
        } else {
            activityManager?.registerDefault()
        }
    }

    val ok get() = this !== defaultShizukuContext
    fun destroy() {
        serviceWrapper?.destroy()
        if (activityTaskManager != null) {
            activityTaskManager.unregisterDefault()
        } else {
            activityManager?.unregisterDefault()
        }
    }

    val states = listOf(
        "IUserService" to serviceWrapper,
        "IActivityManager" to activityManager,
        "IActivityTaskManager" to activityTaskManager,
        "IAppOpsService" to appOpsService,
        "IInputManager" to inputManager,
        "IPackageManager" to packageManager,
        "IUserManager" to userManager,
    )

    fun grantSelf() {
        appOpsService?.allowAllSelfMode()
        packageManager?.allowAllSelfPermission()
    }
}

private val defaultShizukuContext = ShizukuContext(
    serviceWrapper = null,
    packageManager = null,
    userManager = null,
    activityManager = null,
    activityTaskManager = null,
    appOpsService = null,
    inputManager = null,
)

val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

val shizukuContextFlow = MutableStateFlow(defaultShizukuContext)

fun safeGetTopCpn(): ComponentName? = shizukuContextFlow.value.run {
    (activityTaskManager?.getTasks(1) ?: activityManager?.getTasks(1))?.firstOrNull()?.topActivity
}

fun shizukuCheckGranted(): Boolean {
    val granted = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }
    if (!granted) return false
    val u = shizukuContextFlow.value.packageManager ?: SafePackageManager.newBinder()
    return u?.isSafeMode != null
}

val updateBinderMutex = MutexState()
private fun updateShizukuBinder() = updateBinderMutex.launchTry(appScope, Dispatchers.IO) {
    if (shizukuUsedFlow.value) {
        if (!app.justStarted && isActivityVisible()) {
            toast("正在连接 Shizuku 服务...")
        }
        shizukuContextFlow.value = ShizukuContext(
            serviceWrapper = buildServiceWrapper(),
            packageManager = SafePackageManager.newBinder(),
            userManager = SafeUserManager.newBinder(),
            activityManager = SafeActivityManager.newBinder(),
            activityTaskManager = SafeActivityTaskManager.newBinder(),
            appOpsService = SafeAppOpsService.newBinder(),
            inputManager = SafeInputManager.newBinder(),
        )
        if (isActivityVisible()) {
            val delayMillis = if (app.justStarted) 1200L else 0L
            val newValue = shizukuContextFlow.value
            if (newValue.serviceWrapper == null) {
                if (newValue.packageManager != null) {
                    toast("Shizuku 服务连接部分失败", delayMillis)
                } else {
                    toast("Shizuku 服务连接失败", delayMillis)
                }
            } else {
                toast("Shizuku 服务连接成功", delayMillis)
            }
        }
    } else if (shizukuContextFlow.value.ok) {
        shizukuContextFlow.value.destroy()
        shizukuContextFlow.value = defaultShizukuContext
        if (isActivityVisible()) {
            toast("Shizuku 服务已断开")
        }
    }
}

fun initShizuku() {
    Shizuku.addBinderReceivedListener {
        LogUtils.d("Shizuku.addBinderReceivedListener")
        appScope.launchTry(Dispatchers.IO) {
            shizukuOkState.updateAndGet()
        }
    }
    Shizuku.addBinderDeadListener {
        LogUtils.d("Shizuku.addBinderDeadListener")
        shizukuOkState.stateFlow.value = false
    }
    appScope.launchTry {
        shizukuUsedFlow.collect { updateShizukuBinder() }
    }
}
