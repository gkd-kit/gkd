package li.songe.gkd.priv;

import android.graphics.Bitmap;
import android.graphics.Rect;

interface IUserService {
    void destroy() = 16777114;
    Bitmap takeScreenshot(in Rect crop, int rotation) = 1;
}
