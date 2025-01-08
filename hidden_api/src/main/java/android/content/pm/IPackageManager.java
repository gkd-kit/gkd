package android.content.pm;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

@SuppressWarnings("unused")
public interface IPackageManager extends IInterface {

    ApplicationInfo getApplicationInfo(String packageName, long flags, int userId);

    PackageInfo getPackageInfo(String packageName, long flags, int userId);

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId);

    ParceledListSlice<PackageInfo> getInstalledPackages(long flags, int userId);

    ParceledListSlice<IntentFilter> getAllIntentFilters(String packageName);

    ActivityInfo getActivityInfo(ComponentName component, int flags) throws PackageManager.NameNotFoundException;

    void grantRuntimePermission(String packageName, String permName, int userId);

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new IllegalArgumentException("Stub!");
        }
    }
}
