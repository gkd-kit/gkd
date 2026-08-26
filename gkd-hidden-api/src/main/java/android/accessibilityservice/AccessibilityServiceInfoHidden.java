package android.accessibilityservice;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.os.Build;

import androidx.annotation.RequiresApi;

import li.songe.remap.RemapType;

@RemapType(AccessibilityServiceInfo.class)
public class AccessibilityServiceInfoHidden {
    public static int FLAG_FORCE_DIRECT_BOOT_AWARE;

    public AccessibilityServiceInfoHidden(ResolveInfo resolveInfo, Context context) {
        throw new RuntimeException();
    }

    public void setCapabilities(int capabilities) {
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void setAccessibilityTool(boolean isAccessibilityTool) {
    }
}
