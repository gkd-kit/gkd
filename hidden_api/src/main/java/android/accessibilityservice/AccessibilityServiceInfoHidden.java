package android.accessibilityservice;

import android.os.Build;

import androidx.annotation.RequiresApi;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(AccessibilityServiceInfo.class)
public class AccessibilityServiceInfoHidden {
    public static int FLAG_FORCE_DIRECT_BOOT_AWARE;

    public void setCapabilities(int capabilities) {
        throw new RuntimeException();
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void setAccessibilityTool(boolean isAccessibilityTool) {
        throw new RuntimeException();
    }
}
