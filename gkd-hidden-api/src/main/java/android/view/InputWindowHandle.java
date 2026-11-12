package android.view;

import android.graphics.Region;
import android.os.IBinder;

public class InputWindowHandle {
    public String packageName;
    public int displayId;
    public int layoutParamsType;
    public int inputConfig;
    public Region touchableRegion;

    public IBinder getWindowToken() {
        throw new UnsupportedOperationException();
    }
}
