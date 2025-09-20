package android.app;

import android.os.IBinder;

/**
 * @noinspection unused
 */
public interface ITaskStackListener {
    abstract class Stub extends android.os.Binder implements ITaskStackListener {
        public static ITaskStackListener asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    // 应用->桌面不会回调，分屏下切换窗口不会回调，但从最近任务界面移除窗口会回调
    void onTaskStackChanged();

    // android8 - android9
    void onTaskMovedToFront(int taskId);

    // android10+
    void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo);
}