package li.songe.gkd.composition

import android.view.accessibility.AccessibilityEvent

interface CanOnAccessibilityEvent {
    fun onAccessibilityEvent(f: (AccessibilityEvent) -> Unit): Boolean
}