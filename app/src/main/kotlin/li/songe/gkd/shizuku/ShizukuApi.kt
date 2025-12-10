package li.songe.gkd.shizuku


import android.content.ComponentName
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
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
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.MutexState
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.runMainPost
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

inline fun <T> safeInvokeMethod(
    block: () -> T
): T? = try {
    block()
} catch (e: IllegalStateException) {
    // https://github.com/RikkaApps/Shizuku-API/blob/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5/api/src/main/java/rikka/shizuku/Shizuku.java#L430
    if (e.message == "binder haven't been received") {
        null
    } else {
        throw e
    }
}

fun getStubService(name: String, condition: Boolean): ShizukuBinderWrapper? {
    if (!condition) return null
    val service = SystemServiceHelper.getSystemService(name) ?: return null
    return ShizukuBinderWrapper(service)
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

class ShizukuContext(
    val serviceWrapper: UserServiceWrapper?,
    val packageManager: SafePackageManager?,
    val userManager: SafeUserManager?,
    val activityManager: SafeActivityManager?,
    val activityTaskManager: SafeActivityTaskManager?,
    val appOpsService: SafeAppOpsService?,
    val inputManager: SafeInputManager?,
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
    )

    fun grantSelf() {
        appOpsService?.allowAllSelfMode()
        packageManager?.allowAllSelfPermission()
    }

    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        return serviceWrapper?.tap(x, y, duration) ?: (inputManager?.tap(x, y, duration) != null)
    }

    fun topCpn(): ComponentName? {
        return (activityTaskManager?.getTasks(1)
            ?: activityManager?.getTasks(1))?.firstOrNull()?.topActivity
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

private val shizukuUsedFlow by lazy {
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
        updatePermissionState()
        if (isActivityVisible()) {
            val delayMillis = if (app.justStarted) 1200L else 0L
            val newValue = shizukuContextFlow.value
            if (newValue.serviceWrapper == null) {
                if (newValue.packageManager != null) {
                    runMainPost(delayMillis) { toast("Shizuku 服务连接部分失败") }
                } else {
                    runMainPost(delayMillis) { toast("Shizuku 服务连接失败") }
                }
            } else {
                runMainPost(delayMillis) { toast("Shizuku 服务连接成功") }
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
