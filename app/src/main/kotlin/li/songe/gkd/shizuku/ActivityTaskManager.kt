package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.content.ComponentName
import android.view.Display
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.util.checkExistClass
import kotlin.reflect.typeOf

private var tasksFcType: Int? = null
private fun IActivityTaskManager.compatGetTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo> {
    tasksFcType = tasksFcType ?: findCompatMethod(
        "getTasks",
        listOf(
            1 to listOf(typeOf<Int>()),
            3 to listOf(typeOf<Int>(), typeOf<Boolean>(), typeOf<Boolean>()),
            4 to listOf(typeOf<Int>(), typeOf<Boolean>(), typeOf<Boolean>(), typeOf<Int>()),
        )
    )
    return when (tasksFcType) {
        1 -> getTasks(maxNum)
        3 -> getTasks(maxNum, false, false)
        4 -> getTasks(maxNum, false, false, Display.INVALID_DISPLAY)
        else -> emptyList()
    }
}

object SafeTaskListener {
    val isAvailable: Boolean
        get() = checkExistClass("android.app.ITaskStackListener")
    val instance by lazy { FixedTaskStackListener() }
}

class SafeActivityTaskManager(private val value: IActivityTaskManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.app.IActivityTaskManager")

        fun newBinder() = getStubService(
            "activity_task",
            isAvailable,
        )?.let {
            SafeActivityTaskManager(IActivityTaskManager.Stub.asInterface(it))
        }
    }

    fun getTopCpn(): ComponentName? = safeInvokeMethod {
        value.compatGetTasks(1).firstOrNull()?.topActivity
    }

    fun registerDefault() {
        if (!SafeTaskListener.isAvailable) return
        safeInvokeMethod {
            value.registerTaskStackListener(SafeTaskListener.instance)
        }
    }

    fun unregisterDefault() {
        if (!shizukuOkState.stateFlow.value) return
        if (!SafeTaskListener.isAvailable) return
        safeInvokeMethod {
            value.unregisterTaskStackListener(SafeTaskListener.instance)
        }
    }
}
