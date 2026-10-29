package li.gkd.aidl;

import android.graphics.Bitmap;
import android.graphics.Rect;
import li.gkd.aidl.ISystemSurfaceListener;

interface IUserService {
    void destroy() = 16777114;
    Bitmap takeScreenshot(in Rect crop, int rotation) = 1;
    void setSystemSurfaceListener(ISystemSurfaceListener listener) = 2;
}
