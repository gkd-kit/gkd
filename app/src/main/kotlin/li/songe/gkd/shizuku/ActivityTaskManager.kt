package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.view.Display
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.data.DeviceInfo
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.service.TopActivity
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.typeOf

/**
 * -1: invalid fc
 * 1: (int) -> List<Task>
 * 3: (int, boolean, boolean) -> List<Task>
 * 4: (int, boolean, boolean, int) -> List<Task>
 */
private var getTasksFcType: Int? = null
private fun IActivityTaskManager.compatGetTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo> {
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
            1 -> this.getTasks(maxNum)
            3 -> this.getTasks(maxNum, false, true)
            4 -> this.getTasks(maxNum, false, true, Display.DEFAULT_DISPLAY)
            else -> emptyList()
        }
    } catch (e: Throwable) {
        LogUtils.d(e)
        emptyList()
    }
}

// https://github.com/gkd-kit/gkd/issues/44
// fix java.lang.ClassNotFoundException:Didn't find class "android.app.IActivityTaskManager" on path: DexPathList
interface SafeActivityTaskManager {
    fun compatGetTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo>
    fun compatGetTasks(): List<ActivityManager.RunningTaskInfo>
}

private fun newActivityTaskManager(): SafeActivityTaskManager? {
    val service = SystemServiceHelper.getSystemService("activity_task")
    if (service == null) {
        LogUtils.d("shizuku 无法获取 activity_task")
        return null
    }
    val manager = service.let(::ShizukuBinderWrapper).let(IActivityTaskManager.Stub::asInterface)
    return object : SafeActivityTaskManager {
        override fun compatGetTasks(maxNum: Int) = manager.compatGetTasks(maxNum)
        override fun compatGetTasks() = manager.compatGetTasks()
    }
}

private val shizukuActivityUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, storeFlow) { shizukuOk, store ->
        shizukuOk && store.enableShizukuActivity
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

private val activityTaskManagerFlow by lazy<StateFlow<SafeActivityTaskManager?>> {
    val stateFlow = MutableStateFlow<SafeActivityTaskManager?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuActivityUsedFlow.collect {
            stateFlow.value = if (it) newActivityTaskManager() else null
        }
    }
    stateFlow
}

fun shizukuCheckActivity(): Boolean {
    return (try {
        newActivityTaskManager()?.compatGetTasks(1)?.isNotEmpty() == true
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    })
}

fun safeGetTopActivity(): TopActivity? {
    if (!shizukuActivityUsedFlow.value) return null
    try {
        val taskManager = activityTaskManagerFlow.value ?: return null
        val top = taskManager.compatGetTasks(1).lastOrNull()?.topActivity ?: return null
        return TopActivity(appId = top.packageName, activityId = top.className)
    } catch (e: Throwable) {
        e.printStackTrace()
        return null
    }
}
