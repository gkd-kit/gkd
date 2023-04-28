package li.songe.gkd.accessibility

import android.view.accessibility.AccessibilityNodeInfo

data class NodeSnapshot(
    val root: AccessibilityNodeInfo? = null,
    val activityId: String? = null,
) {
    val appId by lazy { root?.packageName?.toString() }
}