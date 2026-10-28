package android.view;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.window.ScreenCapture;
import android.window.ScreenCaptureInternal;

import androidx.annotation.RequiresApi;

public interface IWindowManager extends IInterface {
    abstract class Stub extends Binder implements IWindowManager {
        public static IWindowManager asInterface(IBinder obj) {
            throw new RuntimeException();
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void captureDisplay(int displayId, ScreenCapture.CaptureArgs captureArgs, ScreenCapture.ScreenCaptureListener listener);

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    void captureDisplay(int displayId, ScreenCaptureInternal.CaptureArgs captureArgs, ScreenCaptureInternal.ScreenCaptureListener listener);
}
