package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Parcel
import li.songe.gkd.a11y.ActivityScene
import li.songe.gkd.a11y.updateTopActivity

object FixedTaskStackListener : ITaskStackListener.Stub() {

    // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = try {
        super.onTransact(code, data, reply, flags)
    } catch (_: Throwable) {
        true
    }

    override fun onTaskStackChanged() {
        val cpn = shizukuContextFlow.value.topCpn() ?: return
        synchronized(this) {
            if (lastFront.first > 0 && lastFront.second == cpn && System.currentTimeMillis() - lastFront.first > 200) {
                lastFront = defaultFront
                return
            }
        }
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            scene = ActivityScene.TaskStack,
        )
    }

    private val defaultFront = 0L to ComponentName("", "")
    private var lastFront = defaultFront
    private fun onTaskMovedToFrontCompat(cpn: ComponentName? = null) {
        val cpn = cpn ?: shizukuContextFlow.value.topCpn() ?: return
        synchronized(this) {
            lastFront = System.currentTimeMillis() to cpn
        }
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            scene = ActivityScene.TaskStack,
        )
    }

    override fun onTaskMovedToFront(taskId: Int) {
        onTaskMovedToFrontCompat()
    }

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
        onTaskMovedToFrontCompat(taskInfo.topActivity)
    }
}
