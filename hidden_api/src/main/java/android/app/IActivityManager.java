package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

@SuppressWarnings("unused")
public interface IActivityManager extends IInterface {
    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);
}
