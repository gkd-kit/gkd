package android.os;

import android.content.pm.UserInfo;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

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

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R, message = "NoSuchMethodError")
    List<UserInfo> getUsers(boolean excludeDying);

    @RequiresApi(Build.VERSION_CODES.R)
    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated);
}
