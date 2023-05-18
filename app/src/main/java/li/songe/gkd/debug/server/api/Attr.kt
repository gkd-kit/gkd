package li.songe.gkd.debug.server.api

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

@Serializable
data class Attr(
    val id: String? = null,
    val className: String? = null,
    val childCount: Int = 0,
    val text: String? = null,
    val isClickable: Boolean = false,
    val desc: String? = null,
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
