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
                mapOf(
                    listOf(Int::class.java) to 1,
                    listOf(Int::class.java, Boolean::class.java, Boolean::class.java) to 2,
                    listOf(
                        Int::class.java,
                        Boolean::class.java,
                        Boolean::class.java,
                        Int::class.java
                    ) to 3,
                )
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
