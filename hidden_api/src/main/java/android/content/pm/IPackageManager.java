package android.content.pm;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.IInterface;

import java.util.List;

@SuppressWarnings("unused")
public interface IPackageManager extends IInterface {
    ComponentName getHomeActivities(List<ResolveInfo> outHomeCandidates);

    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId);

    ApplicationInfo getApplicationInfo(String packageName, long flags, int userId);

    PackageInfo getPackageInfo(String packageName, long flags, int userId);

    abstract class Stub {

        public static IPackageManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
