package li.songe.gkd.shizuku

import android.app.ActivityManager
import android.app.ActivityManagerHidden
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.IBinder
import android.window.TaskSnapshot

class TaskListener(private val onStackChanged: () -> Unit) : ITaskStackListener.Stub() {
    override fun onTaskStackChanged() = onStackChanged()

    override fun onActivityPinned(
        packageName: String?,
        userId: Int,
        taskId: Int,
        stackId: Int
    ) {
    }

    override fun onActivityUnpinned() {
    }

    override fun onPinnedActivityRestartAttempt(clearedTask: Boolean) {
    }

    override fun onPinnedStackAnimationStarted() {
    }

    override fun onPinnedStackAnimationEnded() {
    }

    override fun onActivityForcedResizable(
        packageName: String?,
        taskId: Int,
        reason: Int
    ) {
    }

    override fun onActivityDismissingDockedStack() {
    }

    override fun onActivityLaunchOnSecondaryDisplayFailed(
        taskInfo: ActivityManager.RunningTaskInfo?,
        requestedDisplayId: Int
    ) {
    }

    override fun onActivityLaunchOnSecondaryDisplayRerouted(
        taskInfo: ActivityManager.RunningTaskInfo?,
        requestedDisplayId: Int
    ) {
    }

    override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {
    }

    override fun onTaskRemoved(taskId: Int) {
    }

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo?) {}

    override fun onTaskDescriptionChanged(taskInfo: ActivityManager.RunningTaskInfo?) {}

    override fun onActivityRequestedOrientationChanged(
        taskId: Int,
        requestedOrientation: Int
    ) {
    }

    override fun onTaskRemovalStarted(taskInfo: ActivityManager.RunningTaskInfo?) {}

    override fun onTaskProfileLocked(taskId: Int, userId: Int) {
    }

    override fun onTaskSnapshotChanged(
        taskId: Int,
        snapshot: ActivityManagerHidden.TaskSnapshot?
    ) {
    }

    override fun onSizeCompatModeActivityChanged(
        displayId: Int,
        activityToken: IBinder?
    ) {
    }

    override fun onBackPressedOnTaskRoot(taskInfo: ActivityManager.RunningTaskInfo?) {}

    override fun onTaskDisplayChanged(taskId: Int, newDisplayId: Int) {}

    override fun onActivityRestartAttempt(
        task: ActivityManager.RunningTaskInfo?,
        homeTaskVisible: Boolean,
        clearedTask: Boolean,
        wasVisible: Boolean
    ) {
    }

    override fun onSingleTaskDisplayDrawn(displayId: Int) {
    }

    override fun onSingleTaskDisplayEmpty(displayId: Int) {
    }

    override fun onRecentTaskListUpdated() {}

    override fun onRecentTaskListFrozenChanged(frozen: Boolean) {}

    override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {}

    override fun onTaskRequestedOrientationChanged(
        taskId: Int,
        requestedOrientation: Int
    ) {
    }

    override fun onActivityRotation(displayId: Int) {}

    override fun onTaskSnapshotChanged(taskId: Int, snapshot: TaskSnapshot?) {}

    override fun onTaskMovedToBack(taskInfo: ActivityManager.RunningTaskInfo?) {}

    override fun onLockTaskModeChanged(mode: Int) {}
}