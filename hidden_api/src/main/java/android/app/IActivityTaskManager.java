package android.app;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * @noinspection unused
 */
//@RequiresApi(api = Build.VERSION_CODES.Q)
public interface IActivityTaskManager extends IInterface {
    abstract class Stub extends Binder implements IActivityTaskManager {
        public static IActivityTaskManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R, message = "NoSuchMethodError")
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU, message = "NoSuchMethodError")
    @RequiresApi(Build.VERSION_CODES.S)
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, boolean filterOnlyVisibleRecents, boolean keepIntentExtra, int displayId);

    void registerTaskStackListener(ITaskStackListener listener);

    void unregisterTaskStackListener(ITaskStackListener listener);
}
