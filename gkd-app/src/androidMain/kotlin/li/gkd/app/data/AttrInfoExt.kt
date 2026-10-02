package li.gkd.app.data

import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.a11y.compatChecked
import li.gkd.app.model.AttrInfo
import li.gkd.app.priv.toHidden

fun AccessibilityNodeInfo.toAttrInfo(
    index: Int,
    depth: Int,
): AttrInfo {
    val rect = this.toHidden.boundsInScreen
    val appId = this.packageName?.toString() ?: ""
    val id: String? = this.viewIdResourceName
    val idPrefix = "$appId:id/"
    val vid = if (id != null && id.startsWith(idPrefix)) {
        id.substring(idPrefix.length)
    } else {
        // 此处不使用 id 是因为某些节点的 id 没有 appId:id/ 前缀
        null
    }
    return AttrInfo(
        id = id,
        vid = vid,
        name = this.className?.toString(),
        text = this.text?.toString(),
        desc = this.contentDescription?.toString(),

        clickable = this.isClickable,
        focusable = this.isFocusable,
        checkable = this.isCheckable,
        checked = this.compatChecked,
        editable = this.isEditable,
        longClickable = this.isLongClickable,
        visibleToUser = this.isVisibleToUser,

        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,

        width = rect.width(),
        height = rect.height(),

        childCount = this.childCount,

        index = index,
        depth = depth,
    )
}
