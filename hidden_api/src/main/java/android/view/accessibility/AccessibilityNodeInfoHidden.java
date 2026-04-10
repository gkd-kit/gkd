package android.view.accessibility;

import android.graphics.Rect;

import li.songe.remap.RemapType;

/**
 * @noinspection unused
 */
@RemapType(AccessibilityNodeInfo.class)
public class AccessibilityNodeInfoHidden {
    public Rect getBoundsInScreen() {
        throw new RuntimeException();
    }
}
