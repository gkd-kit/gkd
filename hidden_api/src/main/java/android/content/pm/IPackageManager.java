package android.content.pm;

import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

@SuppressWarnings("unused")
public interface IPackageManager extends IInterface {
    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder binder) {
            throw new IllegalArgumentException("Stub!");
        }
    }
    // android8 - android12 -> int flags
    // android13+ -> long flags

    // android8 - android12
    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId);

    // android13+
    ParceledListSlice<PackageInfo> getInstalledPackages(long flags, int userId);

    ParceledListSlice<IntentFilter> getAllIntentFilters(String packageName);

}
