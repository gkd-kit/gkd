package android.content.pm;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;


public interface IPackageManager extends IInterface {
    ComponentName getHomeActivities(List<ResolveInfo> outHomeCandidates) throws RemoteException;

    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId) throws RemoteException;

    ApplicationInfo getApplicationInfo(String packageName, long flags, int userId) throws RemoteException;

    PackageInfo getPackageInfo(String packageName, long flags, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {

        public static IPackageManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
