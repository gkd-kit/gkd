package li.songe.gkd.debug.server.api

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

@Serializable
data class Attr(
    val id: String?,
    val className: String?,
    val childCount: Int,
    val text: String?,
    val isClickable: Boolean,
    val desc: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        private val rect = Rect()
        fun info2data(
            nodeInfo: AccessibilityNodeInfo,
        ): Attr {
            nodeInfo.getBoundsInScreen(rect)
            return Attr(
                id = nodeInfo.viewIdResourceName,
                className = nodeInfo.className?.toString(),
                childCount = nodeInfo.childCount,
                text = nodeInfo.text?.toString(),
                isClickable = nodeInfo.isClickable,
                desc = nodeInfo.contentDescription?.toString(),
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }
    }
}
