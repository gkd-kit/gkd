package android.app;

import android.os.Build;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

/**
 * @noinspection unused
 */
@RemapType(UiAutomation.class)
public class UiAutomationHidden {

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static int FLAG_NOT_ACCESSIBILITY_TOOL;

    public UiAutomationHidden(Looper looper, IUiAutomationConnection connection) {
        throw new RuntimeException();
    }

    public void connect() {
        throw new RuntimeException();
    }

    public void connect(int flag) {
        throw new RuntimeException();
    }

    public void disconnect() {
        throw new RuntimeException();
    }
}
