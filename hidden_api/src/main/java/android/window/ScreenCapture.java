package android.window;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ScreenCapture {
    public static SynchronousScreenCaptureListener createSyncCaptureListener() {
        throw new RuntimeException();
    }

    public static class CaptureArgs {
        public static class Builder<T extends CaptureArgs.Builder<T>> {
            public CaptureArgs build() {
                throw new RuntimeException();
            }

            public T setSourceCrop(Rect sourceCrop) {
                throw new RuntimeException();
            }
        }
    }

    public static class ScreenCaptureListener {
    }

    public static class ScreenshotHardwareBuffer {
        public Bitmap asBitmap() {
            throw new RuntimeException();
        }
    }

    public abstract static class SynchronousScreenCaptureListener extends ScreenCaptureListener {
        public abstract ScreenshotHardwareBuffer getBuffer();
    }
}
