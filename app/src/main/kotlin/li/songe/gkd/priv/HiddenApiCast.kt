@file:Suppress("CAST_NEVER_SUCCEEDS")

package li.songe.gkd.priv

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfoHidden
import android.app.ActivityManager
import android.app.TaskInfoHidden
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

inline val UiAutomationHidden.toPublic get() = this as UiAutomation
inline val UiAutomation.toHidden get() = this as UiAutomationHidden

inline val AccessibilityNodeInfo.toHidden get() = this as AccessibilityNodeInfoHidden

inline val AccessibilityServiceInfoHidden.toPublic get() = this as AccessibilityServiceInfo
inline val AccessibilityServiceInfo.toHidden get() = this as AccessibilityServiceInfoHidden

inline val KeyEvent.toHidden get() = this as KeyEventHidden

inline val MotionEvent.toHidden get() = this as MotionEventHidden

inline val PackageInfo.toHidden get() = this as PackageInfoHidden

inline val ActivityManager.RunningTaskInfo.toHidden get() = this as TaskInfoHidden
