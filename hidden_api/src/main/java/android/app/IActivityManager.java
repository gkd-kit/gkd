package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;

@SuppressWarnings("unused")
public interface IActivityManager extends IInterface {
    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() throws RemoteException;

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum) throws RemoteException;

    List<ActivityManager.RunningTaskInfo> getFilteredTasks(int maxNum, int ignoreActivityType, int ignoreWindowingMode) throws RemoteException;

    void forceStopPackage(String packageName, int userId);

    abstract class Stub extends Binder implements IActivityManager {

        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
