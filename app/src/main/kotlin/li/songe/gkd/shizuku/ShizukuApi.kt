package li.songe.gkd.shizuku


import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.permission.updatePermissionState
import li.songe.gkd.service.ExposeService
import li.songe.gkd.service.StatusService
import li.songe.gkd.service.currentAppBlocked
import li.songe.gkd.service.currentAppUseA11y
import li.songe.gkd.service.updateTopTaskAppId
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.MutexState
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import kotlin.system.exitProcess

inline fun <T> safeInvokeShizuku(
    block: () -> T
): T? = try {
    block()
} catch (_: ShizukuOffException) {
    null
} catch (e: IllegalStateException) {
    // https://github.com/RikkaApps/Shizuku-API/blob/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5/api/src/main/java/rikka/shizuku/Shizuku.java#L430
    if (e.message == "binder haven't been received") {
        null
    } else {
        throw e
    }
}

class ShizukuOffException : IllegalStateException("Shizuku is off")

fun getShizukuService(name: String): ShizukuBinderWrapper? {
    return SystemServiceHelper.getSystemService(name)?.let(::ShizukuBinderWrapper)
}

private fun Method.simpleString(): String {
    return "${name}(${parameterTypes.joinToString(",") { it.name }}):${returnType.name}"
}

fun Class<*>.detectHiddenMethod(
    methodName: String,
    vararg args: Pair<Int, List<Class<*>>>,
): Int {
    val methodsVal = methods
    methodsVal.forEach { method ->
        if (method.name == methodName) {
            val types = method.parameterTypes.toList()
            args.forEach { (value, argTypes) ->
                if (types == argTypes) {
                    return value
                }
            }
        }
    }
    val result = methodsVal.filter { it.name == methodName }
    if (result.isEmpty()) {
        throw NoSuchMethodException("${name}::${methodName} not found")
    } else {
        LogUtils.d("detectHiddenMethod", *result.map { it.simpleString() }.toTypedArray())
        throw NoSuchMethodException("${name}::${methodName} not match")
    }
}

// https://github.com/android-cs/16/blob/main/packages/Shell/AndroidManifest.xml
@RequiresApi(Build.VERSION_CODES.P)
private const val Manifest_permission_MANAGE_APP_OPS_MODES =
    "android.permission.MANAGE_APP_OPS_MODES"

class ShizukuContext(
    val serviceWrapper: UserServiceWrapper?,
    val packageManager: SafePackageManager?,
    val userManager: SafeUserManager?,
    val activityManager: SafeActivityManager?,
    val activityTaskManager: SafeActivityTaskManager?,
    val appOpsService: SafeAppOpsService?,
    val inputManager: SafeInputManager?,
    val a11yManager: SafeAccessibilityManager?,
    val wmManager: SafeWindowManager?,
) {
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
        "IAccessibilityManager" to a11yManager,
        "IWindowManager" to wmManager,
    )

    fun grantSelf() {
        packageManager ?: return
        appOpsService ?: return
        if (AndroidTarget.P && Shizuku.checkRemotePermission(
                Manifest_permission_MANAGE_APP_OPS_MODES
            ) == PackageManager.PERMISSION_DENIED
        ) {
            // 部分 ROM 会限制 ADB 权限
            return
        }
        appOpsService.allowAllSelfMode()
        packageManager.allowAllSelfPermission()
    }

    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return serviceWrapper?.tap(x, y, duration) ?: (inputManager?.tap(x, y, duration) != null)
    }

    fun topCpn(): ComponentName? {
        return (activityTaskManager?.getTasks()
            ?: activityManager?.getTasks())?.firstOrNull()?.topActivity
    }

    init {
        if (activityTaskManager != null) {
            activityTaskManager.registerDefault()
        } else {
            activityManager?.registerDefault()
        }
        grantSelf()
    }
}

private val defaultShizukuContext by lazy {
    ShizukuContext(
        serviceWrapper = null,
        packageManager = null,
        userManager = null,
        activityManager = null,
        activityTaskManager = null,
        appOpsService = null,
        inputManager = null,
        a11yManager = null,
        wmManager = null,
    )
}

val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

val shizukuContextFlow by lazy { MutableStateFlow(defaultShizukuContext) }

val shizukuUsedFlow by lazy {
    combine(
        shizukuGrantedState.stateFlow,
        storeFlow.map { it.enableShizuku },
    ) { a, b ->
        a && b
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val updateBinderMutex = MutexState()
private fun updateShizukuBinder() = updateBinderMutex.launchTry(appScope, Dispatchers.IO) {
    if (shizukuUsedFlow.value) {
        if (!app.justStarted) {
            toast("正在连接 Shizuku 服务...")
        }
        val shizukuContext = ShizukuContext(
            serviceWrapper = buildServiceWrapper(),
            packageManager = SafePackageManager.newBinder(),
            userManager = SafeUserManager.newBinder(),
            activityManager = SafeActivityManager.newBinder(),
            activityTaskManager = SafeActivityTaskManager.newBinder(),
            appOpsService = SafeAppOpsService.newBinder(),
            inputManager = SafeInputManager.newBinder(),
            a11yManager = SafeAccessibilityManager.newBinder(),
            wmManager = SafeWindowManager.newBinder(),
        )
        shizukuContextFlow.value = shizukuContext
        shizukuContext.topCpn()?.let { cpn ->
            updateTopTaskAppId(cpn.packageName)
        }
        if (
            storeFlow.value.useAutomation &&
            !currentAppBlocked &&
            !currentAppUseA11y
        ) {
            AutomationService.tryConnect(true)
        }
        updatePermissionState()
        if (StatusService.needRestart) {
            //
            shizukuContext.activityManager?.startForegroundService(ExposeService.exposeIntent(expose = -1))
        }
        val delayMillis = if (app.justStarted) 1200L else 0L
        if (shizukuContext.serviceWrapper == null) {
            if (shizukuContext.packageManager != null) {
                toast("Shizuku 服务连接部分失败", delayMillis = delayMillis)
            } else {
                toast("Shizuku 服务连接失败", delayMillis = delayMillis)
            }
        } else {
            toast("Shizuku 服务连接成功", delayMillis = delayMillis)
        }
    } else if (shizukuContextFlow.value.ok) {
        val willRelaunch = uiAutomationFlow.value != null && !shizukuGrantedState.updateAndGet()
        if (willRelaunch) {
            // 需要重启应用让系统释放 UiAutomation
            killRelaunchApp()
        } else {
            uiAutomationFlow.value?.shutdown(true)
            shizukuContextFlow.value.destroy()
            shizukuContextFlow.value = defaultShizukuContext
            toast("Shizuku 服务已断开")
        }
    }
}

private suspend fun killRelaunchApp() {
    if (isActivityVisible) {
        toast("Shizuku 断开，重启应用以释放自动化服务", forced = true)
        delay(1500)
        app.startLaunchActivity()
    } else {
        toast("Shizuku 断开，结束应用以释放自动化服务", forced = true)
        delay(1500)
    }
    android.os.Process.killProcess(android.os.Process.myPid())
    exitProcess(0)
}

fun initShizuku() {
    Shizuku.addBinderReceivedListener {
        LogUtils.d("Shizuku.addBinderReceivedListener")
        appScope.launchTry(Dispatchers.IO) {
            shizukuGrantedState.updateAndGet()
        }
    }
    Shizuku.addBinderDeadListener {
        LogUtils.d("Shizuku.addBinderDeadListener")
        shizukuGrantedState.stateFlow.value = false
    }
    appScope.launchTry {
        shizukuUsedFlow.collect { updateShizukuBinder() }
    }
}
