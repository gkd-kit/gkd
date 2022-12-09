package li.songe.gkd.server.api

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

@Serializable
data class AttrData(
    val id: String?,
    val className: String?,
    val childCount: Int,
    val text: String?,
    val isClickable: Boolean,
    val contentDescription: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        private val rect = Rect()
        fun info2data(
            nodeInfo: AccessibilityNodeInfo,
        ): AttrData {
            nodeInfo.getBoundsInScreen(rect)
            return AttrData(
                id = nodeInfo.viewIdResourceName,
                className = nodeInfo.className?.toString(),
                childCount = nodeInfo.childCount,
                text = nodeInfo.text?.toString(),
                isClickable = nodeInfo.isClickable,
                contentDescription = nodeInfo.contentDescription?.toString(),
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom
            )
        }
    }
}
