package li.songe.gkd.shizuku


import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.view.Display
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.composition.CanOnDestroy
import li.songe.gkd.data.DeviceInfo
import li.songe.gkd.util.launchWhile
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.typeOf

/**
 * https://github.com/gkd-kit/gkd/issues/44
 */


fun newActivityTaskManager(): IActivityTaskManager? {
    return SystemServiceHelper.getSystemService("activity_task").let(::ShizukuBinderWrapper)
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
                ToastUtils.showShort("Shizuku获取方法签名错误")
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
        null
    }
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

fun CanOnDestroy.useSafeGetTasksFc(scope: CoroutineScope): () -> List<ActivityManager.RunningTaskInfo>? {
    val shizukuAliveFlow = useShizukuAliveState()
    val shizukuGrantFlow = MutableStateFlow(false)
    scope.launchWhile(Dispatchers.IO) {
        shizukuGrantFlow.value = if (shizukuAliveFlow.value) shizukuIsSafeOK() else false
        delay(3000)
    }
    val shizukuCanUsedFlow = combine(
        shizukuAliveFlow,
        shizukuGrantFlow,
        storeFlow.map(scope) { s -> s.enableShizuku }) { shizukuAlive, shizukuGrant, enableShizuku ->
        enableShizuku && shizukuAlive && shizukuGrant
    }.stateIn(scope, SharingStarted.Eagerly, false)

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