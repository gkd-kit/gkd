package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Parcel
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import li.songe.gkd.a11y.ActivityScene
import li.songe.gkd.a11y.updateTopActivity

class FixedTaskStackListener : ITaskStackListener.Stub() {

    // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = try {
        super.onTransact(code, data, reply, flags)
    } catch (_: Throwable) {
        true
    }

    override fun onTaskStackChanged() {
        val cpn = shizukuContextFlow.value.topCpn() ?: return
        val t = System.currentTimeMillis()
        val skip = defaultFront === lastFront.updateAndGet {
            if (it.first > 0 && t - it.first < 200 && it.second == cpn) {
                defaultFront
            } else {
                it
            }
        }
        if (skip) return
        updateTopActivity(
            appId = cpn.packageName,
            activityId = cpn.className,
            scene = ActivityScene.TaskStack,
        )
    }

    private val defaultFront = 0L to ComponentName("", "")
    private val lastFront = atomic(defaultFront)
    private fun onTaskMovedToFrontCompat(cpn: ComponentName? = null) {
        val cpn = cpn ?: shizukuContextFlow.value.topCpn() ?: return
        lastFront.value = System.currentTimeMillis() to cpn
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
