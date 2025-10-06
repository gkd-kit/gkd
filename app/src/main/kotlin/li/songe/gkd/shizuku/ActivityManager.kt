package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityManager
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass

class SafeActivityManager(private val value: IActivityManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.app.IActivityManager")

        fun newBinder() = getStubService(
            "activity",
            isAvailable,
        )?.let {
            SafeActivityManager(IActivityManager.Stub.asInterface(it))
        }
    }

    fun getTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo> = safeInvokeMethod {
        if (AndroidTarget.P) {
            value.getTasks(maxNum)
        } else {
            value.getTasks(maxNum, 0)
        }
    } ?: emptyList()

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