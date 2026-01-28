package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.content.ContextHidden
import android.view.Display
import li.songe.gkd.util.AndroidTarget

class SafeActivityTaskManager(private val value: IActivityTaskManager) {
    companion object {
        fun newBinder() = if (AndroidTarget.Q) {
            getShizukuService(ContextHidden.ACTIVITY_TASK_SERVICE)?.let {
                SafeActivityTaskManager(IActivityTaskManager.Stub.asInterface(it))
            }
        } else {
            null
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

    fun getTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo>? = safeInvokeShizuku {
        when (getTasksType) {
            1 -> value.getTasks(maxNum)
            2 -> value.getTasks(maxNum, false, false)
            3 -> value.getTasks(maxNum, false, false, Display.INVALID_DISPLAY)
            else -> value.getTasks(maxNum)
        }
    }

    fun registerDefault() {
        safeInvokeShizuku {
            value.registerTaskStackListener(FixedTaskStackListener)
        }
    }

    fun unregisterDefault() {
        safeInvokeShizuku {
            value.unregisterTaskStackListener(FixedTaskStackListener)
        }
    }
}
