package li.gkd.aidl;

import android.graphics.Bitmap;
import android.graphics.Rect;
import li.gkd.aidl.ISystemSurfaceListener;
import li.gkd.aidl.IInstallResultListener;
import android.content.Intent;
import android.content.IntentSender;

interface IUserService {
    void destroy() = 16777114;
    Bitmap takeScreenshot(in Rect crop, int rotation) = 1;
    void setSystemSurfaceListener(ISystemSurfaceListener listener) = 2;
    IntentSender createInstallResultSender(in Intent launchIntent, String callingPackage, int userId, IInstallResultListener listener) = 3;
    @nullable String getAccessibilityWindowPackage(int windowId, int userId) = 4;
}
