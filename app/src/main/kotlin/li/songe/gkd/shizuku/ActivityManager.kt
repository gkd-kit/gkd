package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.IActivityManager
import android.content.Context
import android.content.Intent
import li.songe.gkd.util.AndroidTarget

class SafeActivityManager(private val value: IActivityManager) {
    companion object {
        fun newBinder() = getShizukuService(Context.ACTIVITY_SERVICE)?.let {
            SafeActivityManager(IActivityManager.Stub.asInterface(it))
        }
    }

    fun getTasks(maxNum: Int = 1): List<ActivityManager.RunningTaskInfo> = safeInvokeShizuku {
        if (AndroidTarget.P) {
            value.getTasks(maxNum)
        } else {
            value.getTasks(maxNum, 0)
        }
    } ?: emptyList()

    fun startForegroundService(intent: Intent) {
        // 被启动的服务必须设置 android:exported="true"
        // https://github.com/android-cs/16/blob/main/services/core/java/com/android/server/am/ActivityManagerShellCommand.java#L982
        val requireForeground = true
        val callingPackage = "com.android.shell"
        val callingFeatureId: String? = null
        if (AndroidTarget.R) {
            value.startService(
                null,
                intent,
                intent.type,
                requireForeground,
                callingPackage,
                callingFeatureId,
                currentUserId
            )
        } else {
            value.startService(
                null,
                intent,
                intent.type,
                requireForeground,
                callingPackage,
                currentUserId
            )
        }
    }

    fun registerDefault() {
        safeInvokeShizuku {
            value.registerTaskStackListener(SafeTaskListener.instance)
        }
    }

    fun unregisterDefault() {
        safeInvokeShizuku {
            value.unregisterTaskStackListener(SafeTaskListener.instance)
        }
    }
}