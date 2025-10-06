package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.view.Display
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass

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

    fun getTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo>? = safeInvokeMethod {
        if (AndroidTarget.TIRAMISU) {
            value.getTasks(maxNum, false, false, Display.INVALID_DISPLAY)
        } else if (AndroidTarget.S) {
            value.getTasks(maxNum, false, false)
        } else {
            value.getTasks(maxNum)
        }
    }

    fun registerDefault() {
        if (!SafeTaskListener.isAvailable) return
        safeInvokeMethod {
            value.registerTaskStackListener(SafeTaskListener.instance)
        }
    }

    fun unregisterDefault() {
        if (!shizukuGrantedState.stateFlow.value) return
        if (!SafeTaskListener.isAvailable) return
        safeInvokeMethod {
            value.unregisterTaskStackListener(SafeTaskListener.instance)
        }
    }
}
