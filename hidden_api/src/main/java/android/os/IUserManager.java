package android.os;

import android.content.pm.UserInfo;

import java.util.List;

@SuppressWarnings("unused")
public interface IUserManager extends IInterface {
    abstract class Stub extends Binder implements IUserManager {
        public static IUserManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }
    }

    // android8 - android10
    List<UserInfo> getUsers(boolean excludeDying);

    // android11+
    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated);
}
