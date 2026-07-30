package android.content.pm;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

import li.songe.remap.RemapMethod;

public interface IPackageManager extends IInterface {
    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new IllegalArgumentException("Stub!");
        }
    }

    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    ParceledListSlice<PackageInfo> getInstalledPackages(long flags, int userId);

    @RemapMethod("getInstalledPackages")
    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    PackageInfoList getInstalledPackagesV17(long flags, int userId);

    int getApplicationEnabledSetting(String packageName, int userId);
}
