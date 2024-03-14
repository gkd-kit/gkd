package li.songe.gkd.shizuku


import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.content.Context
import android.hardware.input.IInputManager
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
import li.songe.gkd.composition.CanOnDestroy
import li.songe.gkd.data.DeviceInfo
import li.songe.gkd.util.map
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.typeOf

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
    return service.let(::ShizukuBinderWrapper)
        .let(IActivityTaskManager.Stub::asInterface)
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
        shizukuAliveFlow,
        shizukuGrantFlow,
        shizukuEnableFlow
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
        downTime,
        downTime,
        MotionEvent.ACTION_DOWN,
        x, y, 1.0f, 1.0f, 0,
        1.0f, 1.0f, 0, 0
    ) // pressure=1.0f
    val upEvent = MotionEvent.obtain(
        downTime,
        downTime,
        MotionEvent.ACTION_UP,
        x, y, 0f, 1.0f, 0,
        1.0f, 1.0f, 0, 0
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
    shizukuCanUsedFlow: StateFlow<Boolean>,
): (x: Float, y: Float) -> Boolean? {
    val inputManagerFlow = shizukuCanUsedFlow.map(scope) { if (it) newInputManager() else null }
    return { x, y ->
        if (shizukuCanUsedFlow.value) {
            inputManagerFlow.value?.safeClick(x, y)
        } else {
            null
        }
    }
}
