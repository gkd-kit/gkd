package android.os;

import android.content.pm.UserInfo;

import java.util.List;

public interface IUserManager extends IInterface {
    abstract class Stub extends Binder implements IUserManager {
        public static IUserManager asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    List<UserInfo> getUsers(boolean excludeDying);

    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated);
}
