package android.view;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

@RemapType(SurfaceControl.class)
public class SurfaceControlHidden {
    @DeprecatedSinceApi(api = Build.VERSION_CODES.P)
    public static Bitmap screenshot(int width, int height) {
        throw new RuntimeException();
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.S)
    public static Bitmap screenshot(Rect sourceCrop, int width, int height, int rotation) {
        throw new RuntimeException();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static IBinder getInternalDisplayToken() {
        throw new RuntimeException();
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static ScreenshotHardwareBuffer captureDisplay(DisplayCaptureArgs captureArgs) {
        throw new RuntimeException();
    }

    public static class DisplayCaptureArgs {
        public static class Builder {
            @RequiresApi(Build.VERSION_CODES.S)
            public Builder(IBinder displayToken) {
                throw new RuntimeException();
            }

            @RequiresApi(Build.VERSION_CODES.S)
            public Builder setSourceCrop(Rect sourceCrop) {
                throw new RuntimeException();
            }

            @RequiresApi(Build.VERSION_CODES.S)
            public Builder setSize(int width, int height) {
                throw new RuntimeException();
            }

            @RequiresApi(Build.VERSION_CODES.S)
            public DisplayCaptureArgs build() {
                throw new RuntimeException();
            }
        }
    }

    public static class ScreenshotHardwareBuffer {
        @RequiresApi(Build.VERSION_CODES.S)
        public Bitmap asBitmap() {
            throw new RuntimeException();
        }
    }
}
