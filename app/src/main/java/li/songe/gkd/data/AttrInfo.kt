package li.songe.gkd.data

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.gkd.accessibility.getDepth
import li.songe.gkd.accessibility.getIndex

@Serializable
data class AttrInfo(
    val id: String? = null,
    val name: String? = null,
    val text: String? = null,
    val textLen: Int? = text?.length,
    val desc: String? = null,
    val descLen: Int? = desc?.length,
    val isClickable: Boolean = false,
    val childCount: Int = 0,
    val index: Int = 0,
    val depth: Int = 0,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        /**
         * 不要在多线程中使用
         */
        private val rect = Rect()
        fun info2data(
            nodeInfo: AccessibilityNodeInfo,
        ): AttrInfo {
            nodeInfo.getBoundsInScreen(rect)
            return AttrInfo(
                id = nodeInfo.viewIdResourceName,
                name = nodeInfo.className?.toString(),
                text = nodeInfo.text?.toString(),
                desc = nodeInfo.contentDescription?.toString(),
                isClickable = nodeInfo.isClickable,
                childCount = nodeInfo.childCount,
                index = nodeInfo.getIndex(),
                depth = nodeInfo.getDepth(),
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }
    }
}
