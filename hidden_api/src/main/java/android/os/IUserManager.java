package android.os;

import android.content.pm.UserInfo;

import java.util.List;

/**
 * @noinspection unused
 */
public interface IUserManager extends IInterface {
    abstract class Stub extends Binder implements IUserManager {
        public static IUserManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }
    }

    // android8 - android10, android-16.0.0_r3
    List<UserInfo> getUsers(boolean excludeDying);

    // android11 - android-16.0.0_r2
    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated);
}
