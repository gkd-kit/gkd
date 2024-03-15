package li.songe.gkd.shizuku


import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.input.IInputManager
import android.os.IBinder
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.BuildConfig
import li.songe.gkd.composition.CanOnDestroy
import li.songe.gkd.data.DeviceInfo
import li.songe.gkd.util.json
import li.songe.gkd.util.map
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.typeOf

fun shizukuIsSafeOK(): Boolean {
    return try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}

/**
 * https://github.com/gkd-kit/gkd/issues/44
 */

// Context.ACTIVITY_TASK_SERVICE
private const val ACTIVITY_TASK_SERVICE = "activity_task"

fun newActivityTaskManager(): IActivityTaskManager? {
    val service = SystemServiceHelper.getSystemService(ACTIVITY_TASK_SERVICE)
    if (service == null) {
        LogUtils.d("shizuku 无法获取 $ACTIVITY_TASK_SERVICE")
        return null
    }
    return service.let(::ShizukuBinderWrapper).let(IActivityTaskManager.Stub::asInterface)
}

/**
 * -1: invalid fc
 * 1: (int) -> List<Task>
 * 3: (int, boolean, boolean) -> List<Task>
 * 4: (int, boolean, boolean, int) -> List<Task>
 */
private var getTasksFcType: Int? = null

fun IActivityTaskManager.safeGetTasks(): List<ActivityManager.RunningTaskInfo>? {
    if (getTasksFcType == null) {
        val fcs = this::class.declaredMemberFunctions
        val parameters = fcs.find { d -> d.name == "getTasks" }?.parameters
        if (parameters != null) {
            if (parameters.size == 5 && parameters[1].type == typeOf<Int>() && parameters[2].type == typeOf<Boolean>() && parameters[3].type == typeOf<Boolean>() && parameters[4].type == typeOf<Int>()) {
                getTasksFcType = 4
            } else if (parameters.size == 4 && parameters[1].type == typeOf<Int>() && parameters[2].type == typeOf<Boolean>() && parameters[3].type == typeOf<Boolean>()) {
                getTasksFcType = 3
            } else if (parameters.size == 2 && parameters[1].type == typeOf<Int>()) {
                getTasksFcType = 1
            } else {
                getTasksFcType = -1
                LogUtils.d(DeviceInfo.instance)
                LogUtils.d(fcs)
                toast("Shizuku获取方法签名错误")
            }
        }
    }
    return try {
        // https://bugly.qq.com/v2/crash-reporting/crashes/d0ce46b353/106137?pid=1
        // binder haven't been received
        when (getTasksFcType) {
            1 -> this.getTasks(1)
            3 -> this.getTasks(1, false, true)
            4 -> this.getTasks(1, false, true, Display.DEFAULT_DISPLAY)
            else -> null
        }
    } catch (e: Exception) {
        LogUtils.d(e)
        null
    }
}

fun newInputManager(): IInputManager? {
    val service = SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)
    if (service == null) {
        LogUtils.d("shizuku 无法获取 " + Context.INPUT_SERVICE)
        return null
    }
    return service.let(::ShizukuBinderWrapper).let(IInputManager.Stub::asInterface)
}


fun CanOnDestroy.useShizukuAliveState(): StateFlow<Boolean> {
    val shizukuAliveFlow = MutableStateFlow(Shizuku.pingBinder())
    val receivedListener = Shizuku.OnBinderReceivedListener { shizukuAliveFlow.value = true }
    val deadListener = Shizuku.OnBinderDeadListener { shizukuAliveFlow.value = false }
    Shizuku.addBinderReceivedListener(receivedListener)
    Shizuku.addBinderDeadListener(deadListener)
    onDestroy {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
    }
    return shizukuAliveFlow
}

fun getShizukuCanUsedFlow(
    scope: CoroutineScope,
    shizukuGrantFlow: StateFlow<Boolean>,
    shizukuAliveFlow: StateFlow<Boolean>,
    shizukuEnableFlow: StateFlow<Boolean>,
): StateFlow<Boolean> {
    return combine(
        shizukuAliveFlow, shizukuGrantFlow, shizukuEnableFlow
    ) { shizukuAlive, shizukuGrant, enableShizuku ->
        enableShizuku && shizukuAlive && shizukuGrant
    }.stateIn(scope, SharingStarted.Eagerly, false)
}

fun useSafeGetTasksFc(
    scope: CoroutineScope,
    shizukuCanUsedFlow: StateFlow<Boolean>,
): () -> List<ActivityManager.RunningTaskInfo>? {
    val activityTaskManagerFlow =
        shizukuCanUsedFlow.map(scope) { if (it) newActivityTaskManager() else null }
    return {
        if (shizukuCanUsedFlow.value) {
            // 避免直接访问方法校验 android.app.IActivityTaskManager 类型
            // 报错 java.lang.ClassNotFoundException:Didn't find class "android.app.IActivityTaskManager" on path: DexPathList
            activityTaskManagerFlow.value?.safeGetTasks()
        } else {
            null
        }
    }
}

fun IInputManager.safeClick(x: Float, y: Float): Boolean? {
    // 模拟 abd shell input tap x y 传递的 pressure
    // 下面除了 pressure 的常量来自 MotionEvent obtain 方法
    val downTime = SystemClock.uptimeMillis()
    val downEvent = MotionEvent.obtain(
        downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0
    ) // pressure=1.0f
    val upEvent = MotionEvent.obtain(
        downTime, downTime, MotionEvent.ACTION_UP, x, y, 0f, 1.0f, 0, 1.0f, 1.0f, 0, 0
    ) // pressure=0f
    return try {
        val r1 = injectInputEvent(downEvent, 2)
        val r2 = injectInputEvent(upEvent, 2)
        r1 && r2
    } catch (e: Exception) {
        LogUtils.d(e)
        null
    } finally {
        downEvent.recycle()
        upEvent.recycle()
    }
}

fun useSafeInjectClickEventFc(
    scope: CoroutineScope,
    usedFlow: StateFlow<Boolean>,
): (x: Float, y: Float) -> Boolean? {
    val inputManagerFlow = usedFlow.map(scope) { if (it) newInputManager() else null }
    return { x, y ->
        if (usedFlow.value) {
            inputManagerFlow.value?.safeClick(x, y)
        } else {
            null
        }
    }
}

// 在 大麦 https://i.gkd.li/i/14605104 上测试产生如下 3 种情况
// 1. 点击不生效: 使用传统无障碍屏幕点击, 此种点击可被 大麦 通过 View.setAccessibilityDelegate 屏蔽
// 2. 点击概率生效: 使用 Shizuku 获取到的 InputManager.injectInputEvent 发出点击, 概率失效/生效, 原因未知
// 3. 点击生效: 使用 Shizuku 获取到的 shell input tap x y 发出点击 by useSafeInputTapFc, 暂未找到屏蔽方案
fun useSafeInputTapFc(
    scope: CoroutineScope,
    usedFlow: StateFlow<Boolean>,
): (x: Float, y: Float) -> Boolean? {
    val serviceWrapperFlow = MutableStateFlow<UserServiceWrapper?>(null)
    scope.launch {
        usedFlow.collect {
            if (it) {
                val serviceWrapper = newUserService()
                serviceWrapperFlow.value = serviceWrapper
            } else {
                serviceWrapperFlow.value?.destroy()
                serviceWrapperFlow.value = null
            }
        }
    }
    return { x, y ->
        if (usedFlow.value) {
            try {
                val result = serviceWrapperFlow.value?.userService?.execCommand("input tap $x $y")
                if (result != null) {
                    json.decodeFromString<CommandResult>(result).code == 0
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }
}

data class UserServiceWrapper(
    val userService: IUserService,
    val connection: ServiceConnection,
    val serviceArgs: Shizuku.UserServiceArgs
) {
    fun destroy() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

suspend fun newUserService(): UserServiceWrapper = suspendCoroutine { continuation ->
    val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            BuildConfig.APPLICATION_ID,
            UserService::class.java.name
        )
    ).daemon(false).processNameSuffix(
        "service-for-${if (BuildConfig.DEBUG) "gkd-debug" else "gkd-release"}"
    ).debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)

    var resumeFc: ((UserServiceWrapper) -> Unit)? = { continuation.resume(it) }

    val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            // Shizuku.unbindUserService 并不会移除 connection, 导致后续调用此函数时 此方法仍然被调用
            LogUtils.d("onServiceConnected", componentName)
            resumeFc ?: return
            if (binder?.pingBinder() == true) {
                resumeFc?.invoke(
                    UserServiceWrapper(
                        IUserService.Stub.asInterface(binder),
                        this,
                        serviceArgs
                    )
                )
                resumeFc = null
            } else {
                LogUtils.d("invalid binder for $componentName received")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            LogUtils.d("onServiceDisconnected", componentName)
        }
    }
    Shizuku.bindUserService(serviceArgs, connection)
}