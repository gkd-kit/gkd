package android.window;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
public class ScreenCaptureInternal {
    public static SynchronousScreenCaptureListener createSyncCaptureListener() {
        throw new RuntimeException();
    }

    public static class ScreenCaptureListener {
    }

    public static class ScreenshotHardwareBuffer {
        public Bitmap asBitmap() {
            throw new RuntimeException();
        }

        public HardwareBuffer getHardwareBuffer() {
            throw new RuntimeException();
        }
    }

    public abstract static class SynchronousScreenCaptureListener extends ScreenCaptureListener {
        public abstract ScreenshotHardwareBuffer getBuffer();
    }

    public static class CaptureArgs {
        public static class Builder<T extends ScreenCaptureInternal.CaptureArgs.Builder<T>> {
            public ScreenCaptureInternal.CaptureArgs build() {
                throw new RuntimeException();
            }

            public T setSourceCrop(Rect sourceCrop) {
                throw new RuntimeException();
            }
        }
    }
}
