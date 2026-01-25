package android.view;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.window.ScreenCapture;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
public interface IWindowManager extends IInterface {
    abstract class Stub extends Binder implements IWindowManager {
        public static IWindowManager asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    boolean isRotationFrozen();

    int getDefaultDisplayRotation();

    // https://diff.songe.li/?ref=IWindowManager.freezeRotation
    @DeprecatedSinceApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    void freezeRotation(int rotation);

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void freezeRotation(int rotation, String caller);

    // https://diff.songe.li/?ref=IWindowManager.thawRotation
    @DeprecatedSinceApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    void thawRotation();

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void thawRotation(String caller);

    // https://diff.songe.li/?ref=IWindowManager.captureDisplay
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void captureDisplay(int displayId, ScreenCapture.CaptureArgs captureArgs, ScreenCapture.ScreenCaptureListener listener);

}
