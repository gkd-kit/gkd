package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.view.Display
import li.songe.gkd.permission.shizukuGrantedState
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

        private val getTasksType by lazy {
            IActivityTaskManager::class.java.detectHiddenMethod(
                "getTasks",
                1 to listOf(Int::class.java),
                2 to listOf(Int::class.java, Boolean::class.java, Boolean::class.java),
                3 to listOf(
                    Int::class.java,
                    Boolean::class.java,
                    Boolean::class.java,
                    Int::class.java
                ),
            )
        }
    }

    fun getTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo>? = safeInvokeMethod {
        when (getTasksType) {
            1 -> value.getTasks(maxNum)
            2 -> value.getTasks(maxNum, false, false)
            3 -> value.getTasks(maxNum, false, false, Display.INVALID_DISPLAY)
            else -> value.getTasks(maxNum)
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
