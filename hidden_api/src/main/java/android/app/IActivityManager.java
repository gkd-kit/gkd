package android.app;

import android.content.ComponentName;
import android.content.Intent;
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
public interface IActivityManager extends IInterface {
    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    @DeprecatedSinceApi(api = Build.VERSION_CODES.P)
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum, int flags);

    @RequiresApi(Build.VERSION_CODES.P)
    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void registerTaskStackListener(ITaskStackListener listener);

    void unregisterTaskStackListener(ITaskStackListener listener);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, boolean requireForeground, String callingPackage, int userId);

    @RequiresApi(Build.VERSION_CODES.R)
    ComponentName startService(IApplicationThread caller, Intent service, String resolvedType, boolean requireForeground, String callingPackage, String callingFeatureId, int userId);
}
