package li.gkd.app.priv

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Parcel
import android.view.Display
import java.util.concurrent.atomic.AtomicLong
import li.gkd.app.matching.input.ActivityScene
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.updateTopActivity
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.monotonicTimeMillis

object CompatTaskStackListener : ITaskStackListener.Stub() {

    // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        try {
            super.onTransact(code, data, reply, flags)
        } catch (_: Throwable) {
            true
        }

    private val requests = AtomicLong()

    override fun onTaskStackChanged() = observeTask(false)

    private val defaultFront = 0L to ComponentName("", "")
    private var lastFront = defaultFront

    private fun observeTask(front: Boolean, supplied: ComponentName? = null, taskId: Int? = null) {
        val request = requests.incrementAndGet()
        val context = privilegeContextFlow.value ?: return
        val page = AndroidForegroundState.currentPage
        // A blocked task Binder read must not hold up window or accessibility input.
        val cpn =
            supplied
                ?: context.topTask()?.let {
                    @Suppress("DEPRECATION") if (taskId != null && it.id != taskId) return
                    it.topActivity
                }
                ?: return
        AndroidForegroundState.withTopActivityLock {
            if (
                request != requests.get() ||
                    privilegeContextFlow.value !== context ||
                    page !== AndroidForegroundState.currentPage
            )
                return@withTopActivityLock
            if (front) lastFront = monotonicTimeMillis() to cpn
            else if (
                lastFront.first > 0 &&
                    lastFront.second == cpn &&
                    monotonicTimeMillis() - lastFront.first > 200
            ) {
                lastFront = defaultFront
                return@withTopActivityLock
            }
            updateTopActivity(cpn.packageName, cpn.className, ActivityScene.TaskStack)
        }
    }

    override fun onTaskMovedToFront(taskId: Int) = observeTask(true, taskId = taskId)

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
        if (AndroidTarget.Q && taskInfo.toHidden.displayId != Display.DEFAULT_DISPLAY) {
            return
        }
        observeTask(true, taskInfo.topActivity)
    }
}
