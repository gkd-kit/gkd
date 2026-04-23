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
    }

    fun getTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo>? = safeInvokeShizuku {
        when (HiddenApiType.getTasks) {
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
