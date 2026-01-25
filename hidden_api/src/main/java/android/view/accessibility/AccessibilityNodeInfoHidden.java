package android.view.accessibility;

import android.graphics.Rect;

import dev.rikka.tools.refine.RefineAs;

/**
 * @noinspection unused
 */
@RefineAs(AccessibilityNodeInfo.class)
public class AccessibilityNodeInfoHidden {
    public Rect getBoundsInScreen() {
        throw new RuntimeException();
    }
}
