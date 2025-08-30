package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Parcel
import li.songe.gkd.a11y.updateTopActivity

class FixedTaskStackListener : ITaskStackListener.Stub() {

    // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = try {
        super.onTransact(code, data, reply, flags)
    } catch (_: Throwable) {
        true
    }

    override fun onTaskStackChanged() {
        if (lastFront > 0 && System.currentTimeMillis() - lastFront < 200) {
            lastFront = 0
            return
        }
        val cpn = safeGetTopCpn() ?: return
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            type = 1,
        )
    }

    private var lastFront = 0L
    fun onTaskMovedToFrontCompat(cpn: ComponentName? = null) {
        lastFront = System.currentTimeMillis()
        val cpn = cpn ?: safeGetTopCpn() ?: return
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            type = 2,
        )
    }

    override fun onTaskMovedToFront(taskId: Int) {
        onTaskMovedToFrontCompat()
    }

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
        onTaskMovedToFrontCompat(taskInfo.topActivity)
    }
}
