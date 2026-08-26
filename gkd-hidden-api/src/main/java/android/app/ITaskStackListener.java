package android.app;

import android.os.Binder;

public interface ITaskStackListener {
    abstract class Stub extends Binder implements ITaskStackListener {
    }

    void onTaskStackChanged();

    void onTaskMovedToFront(int taskId);

    void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo);
}
