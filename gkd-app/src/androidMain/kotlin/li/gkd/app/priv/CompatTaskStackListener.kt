package li.gkd.app.priv

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Parcel
import android.view.Display
import java.util.concurrent.atomic.AtomicLong
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.MatchTaskObservation
import li.gkd.app.matching.input.ActivityScene
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.updateTopActivity
import li.gkd.app.util.AndroidTarget

object CompatTaskStackListener : ITaskStackListener.Stub() {

    // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        try {
            super.onTransact(code, data, reply, flags)
        } catch (_: Throwable) {
            true
        }

    private val requests = AtomicLong()

    override fun onTaskStackChanged() = observeTask("onTaskStackChanged")

    private fun observeTask(
        callback: String,
        supplied: ComponentName? = null,
        taskId: Int? = null,
    ) {
        val request = requests.incrementAndGet()
        val diagnostics = MatchingRuntime.coordinator.diagnostics
        fun trace(reason: String, component: ComponentName? = supplied) {
            if (!diagnostics.enabled) return
            diagnostics.record(
                0,
                MatchStage.Root,
                reason,
                taskObservation =
                    MatchTaskObservation(
                        request,
                        callback,
                        component?.packageName,
                        component?.className,
                    ),
            )
        }
        trace("task_callback_received")
        val context =
            privilegeContextFlow.value
                ?: run {
                    trace("task_context_unavailable")
                    return
                }
        val page = AndroidForegroundState.currentPage
        // A blocked task Binder read must not hold up window or accessibility input.
        val cpn =
            supplied
                ?: context.topTask()?.let {
                    @Suppress("DEPRECATION")
                    if (taskId != null && it.id != taskId) {
                        trace("task_id_mismatch", it.topActivity)
                        return
                    }
                    it.topActivity
                }
                ?: run {
                    trace("task_top_unavailable")
                    return
                }
        trace("task_result_received", cpn)
        AndroidForegroundState.withTopActivityLock {
            val discarded =
                when {
                    request != requests.get() -> "task_request_superseded"
                    privilegeContextFlow.value !== context -> "task_context_changed"
                    page !== AndroidForegroundState.currentPage -> "task_page_changed"
                    else -> null
                }
            if (discarded != null) {
                trace(discarded, cpn)
                return@withTopActivityLock
            }
            trace("task_result_applied", cpn)
            updateTopActivity(cpn.packageName, cpn.className, ActivityScene.TaskStack)
        }
    }

    override fun onTaskMovedToFront(taskId: Int) =
        observeTask("onTaskMovedToFront(id)", taskId = taskId)

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
        if (AndroidTarget.Q && taskInfo.toHidden.displayId != Display.DEFAULT_DISPLAY) {
            return
        }
        observeTask("onTaskMovedToFront(info)", taskInfo.topActivity)
    }
}
