package android.app;

import android.accessibilityservice.IAccessibilityServiceClient;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.window.ScreenCapture;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
public interface IUiAutomationConnection {
    abstract class Stub extends Binder implements IUiAutomationConnection {
        public static IUiAutomationConnection asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    void connect(IAccessibilityServiceClient client, int flags);

    void disconnect();

    void shutdown();

    // https://diff.songe.li/?ref=IUiAutomationConnection.takeScreenshot
    @DeprecatedSinceApi(api = Build.VERSION_CODES.P)
    Bitmap takeScreenshot(int width, int height);

    @RequiresApi(Build.VERSION_CODES.P)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.S)
    Bitmap takeScreenshot(Rect crop, int rotation);

    @RequiresApi(Build.VERSION_CODES.S)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    Bitmap takeScreenshot(Rect crop);

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.BAKLAVA)
    boolean takeScreenshot(Rect crop, ScreenCapture.ScreenCaptureListener listener);

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    boolean takeScreenshot(Rect crop, ScreenCapture.ScreenCaptureListener listener, int displayId);
}
