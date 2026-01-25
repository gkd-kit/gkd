package android.accessibilityservice;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * @noinspection unused
 */
public interface IAccessibilityServiceClient extends IInterface {
    abstract class Stub extends Binder implements IAccessibilityServiceClient {
        public static IAccessibilityServiceClient asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }
}
