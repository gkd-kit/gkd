package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

@SuppressWarnings("unused")
public interface IActivityTaskManager extends IInterface {
    // android10+
    abstract class Stub extends Binder implements IActivityTaskManager {
        public static IActivityTaskManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    // android10 - android11
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    // android12
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra);

    // android13
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId);

    void registerTaskStackListener(ITaskStackListener listener);

    void unregisterTaskStackListener(ITaskStackListener listener);
}
