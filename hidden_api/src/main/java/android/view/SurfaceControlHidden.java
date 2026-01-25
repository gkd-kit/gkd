package android.view;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IBinder;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(SurfaceControl.class)
public class SurfaceControlHidden {
    public static IBinder getInternalDisplayToken() {
        throw new RuntimeException();
    }

    public static ScreenshotHardwareBuffer captureDisplay(DisplayCaptureArgs captureArgs) {
        throw new RuntimeException();
    }

    public static Bitmap screenshot(Rect sourceCrop, int width, int height, int rotation) {
        throw new RuntimeException();
    }

    public static Bitmap screenshot(int width, int height) {
        throw new RuntimeException();
    }

    public interface ScreenCaptureListener {
    }

    public static class ScreenshotHardwareBuffer {
        public Bitmap asBitmap() {
            throw new RuntimeException();
        }
    }


    private abstract static class CaptureArgs {
        abstract static class Builder<T extends Builder<T>> {
            public T setSourceCrop(Rect sourceCrop) {
                throw new RuntimeException();
            }
        }
    }

    public static class DisplayCaptureArgs extends CaptureArgs {
        public static class Builder extends CaptureArgs.Builder<Builder> {
            public Builder(IBinder displayToken) {
                throw new RuntimeException();
            }

            public Builder setSize(int width, int height) {
                throw new RuntimeException();
            }

            public DisplayCaptureArgs build() {
                throw new RuntimeException();
            }
        }
    }
}
