package android.view.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.IAccessibilityServiceClient;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
public interface IAccessibilityManager {
    abstract class Stub extends Binder implements IAccessibilityManager {
        public static IAccessibilityManager asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    @DeprecatedSinceApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void registerUiTestAutomationService(IBinder owner, IAccessibilityServiceClient client, AccessibilityServiceInfo info, int flags);

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void registerUiTestAutomationService(IBinder owner, IAccessibilityServiceClient client, AccessibilityServiceInfo info, int userId, int flags);

    void unregisterUiTestAutomationService(IAccessibilityServiceClient client);
}
