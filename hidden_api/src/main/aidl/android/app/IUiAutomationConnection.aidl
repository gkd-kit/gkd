package android.app;

import android.accessibilityservice.IAccessibilityServiceClient;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.InputEvent;
import android.os.ParcelFileDescriptor;

interface IUiAutomationConnection {
    void connect(IAccessibilityServiceClient client, int flags);
    void disconnect();
    boolean injectInputEvent(in InputEvent event, boolean sync);
    void syncInputTransactions();
    boolean setRotation(int rotation);
    Bitmap takeScreenshot(in Rect crop, int rotation);
    void executeShellCommand(String command, in ParcelFileDescriptor sink,
            in ParcelFileDescriptor source);
    oneway void shutdown();
}
