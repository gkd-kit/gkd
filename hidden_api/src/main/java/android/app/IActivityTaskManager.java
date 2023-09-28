package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

@SuppressWarnings("unused")
public interface IActivityTaskManager extends IInterface {
    //    XIAOMI
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra);

    // https://github.com/gkd-kit/gkd/issues/58#issuecomment-1736843795
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId);

    // https://github.com/gkd-kit/gkd/issues/58#issuecomment-1732245703
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    abstract class Stub extends Binder implements IActivityTaskManager {
        public static IActivityTaskManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
