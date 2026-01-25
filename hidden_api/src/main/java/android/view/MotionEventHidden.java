package android.view;

import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(MotionEvent.class)
public class MotionEventHidden {
    @RequiresApi(Build.VERSION_CODES.Q)
    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount, MotionEvent.PointerProperties[] pointerProperties, MotionEvent.PointerCoords[] pointerCoords, int metaState, int buttonState, float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int displayId, int flags) {
        throw new RuntimeException();
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    public void setDisplayId(int displayId) {
        throw new RuntimeException();
    }
}
