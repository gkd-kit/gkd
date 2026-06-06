package android.window;

import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * @version android-16.0.0_r4
 * @noinspection unused
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class ScreenCaptureInternal {
    public static class ScreenCaptureListener {
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
