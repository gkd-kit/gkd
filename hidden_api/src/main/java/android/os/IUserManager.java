package android.os;

import android.content.pm.UserInfo;

import java.util.List;

@SuppressWarnings("unused")
public interface IUserManager extends IInterface {

    List<UserInfo> getUsers(boolean excludeDying);

    //    @RequiresApi(Build.VERSION_CODES.R)
    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated);

    abstract class Stub extends Binder implements IUserManager {
        public static IUserManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }
    }
}
