package android.content.pm;

import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
public interface IPackageManager extends IInterface {
    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new IllegalArgumentException("Stub!");
        }
    }

    boolean isSafeMode();

    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU, message = "NoSuchMethodError")
    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<PackageInfo> getInstalledPackages(long flags, int userId);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU, message = "NoSuchMethodError")
    PackageInfo getPackageInfo(String packageName, int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    PackageInfo getPackageInfo(String packageName, long flags, int userId);

    ParceledListSlice<IntentFilter> getAllIntentFilters(String packageName);

    void grantRuntimePermission(String packageName, String permissionName, int userId);

}
