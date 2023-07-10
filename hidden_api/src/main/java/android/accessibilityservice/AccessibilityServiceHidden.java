
package android.accessibilityservice;

import android.graphics.Region;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import dev.rikka.tools.refine.RefineAs;

@SuppressWarnings("unused")
@RefineAs(AccessibilityService.class)
public class AccessibilityServiceHidden {

    public interface Callbacks {
        void onAccessibilityEvent(AccessibilityEvent event);

        void onInterrupt();

        void onServiceConnected();

        void init(int connectionId, IBinder windowToken);

        boolean onGesture(int gestureId);

        boolean onKeyEvent(KeyEvent event);

        void onMagnificationChanged(int displayId, Region region,
                                    float scale, float centerX, float centerY);

        void onSoftKeyboardShowModeChanged(int showMode);

        void onPerformGestureResult(int sequence, boolean completedSuccessfully);

        void onFingerprintCapturingGesturesChanged(boolean active);

        void onFingerprintGesture(int gesture);

        void onAccessibilityButtonClicked();

        void onAccessibilityButtonAvailabilityChanged(boolean available);
    }
}
