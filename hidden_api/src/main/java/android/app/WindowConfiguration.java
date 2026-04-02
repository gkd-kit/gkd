package android.app;

import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * @noinspection unused
 */
@RequiresApi(Build.VERSION_CODES.P)
public class WindowConfiguration {
    public Rect getBounds() {
        throw new RuntimeException();
    }

    public Rect getAppBounds() {
        throw new RuntimeException();
    }
}
