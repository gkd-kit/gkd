@file:Suppress("CAST_NEVER_SUCCEEDS")

package li.songe.gkd.shizuku

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfoHidden
import android.app.UiAutomation
import android.app.UiAutomationHidden
import android.content.pm.PackageInfo
import android.content.pm.PackageInfoHidden
import android.view.KeyEvent
import android.view.KeyEventHidden
import android.view.MotionEvent
import android.view.MotionEventHidden
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfoHidden

// Ignoring an implementation of the method `a.getCasted(b)` because it has multiple definitions

inline val UiAutomationHidden.castedHidden get() = this as UiAutomation
inline val UiAutomation.casted get() = this as UiAutomationHidden

inline val AccessibilityNodeInfo.casted get() = this as AccessibilityNodeInfoHidden

inline val AccessibilityServiceInfo.casted get() = this as AccessibilityServiceInfoHidden

inline val KeyEvent.casted get() = this as KeyEventHidden

inline val MotionEvent.casted get() = this as MotionEventHidden

inline val PackageInfo.casted get() = this as PackageInfoHidden
