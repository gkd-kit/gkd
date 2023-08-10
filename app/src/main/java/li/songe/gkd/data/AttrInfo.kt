package li.songe.gkd.data

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.gkd.service.getDepth
import li.songe.gkd.service.getIndex

@Serializable
data class AttrInfo(
    val id: String?,
    val name: String?,
    val text: String?,
    val textLen: Int? = text?.length,
    val desc: String?,
    val descLen: Int? = desc?.length,
    val hint: String?,
    val hintLen: Int? = hint?.length,
    val error: String?,
    val errorLen: Int? = error?.length,
    val inputType: Int?,
    val liveRegion: Int?,

    val enabled: Boolean,
    val clickable: Boolean,
    val checked: Boolean,
    val checkable: Boolean,
    val focused: Boolean,
    val focusable: Boolean,
    val visibleToUser: Boolean,
    val selected: Boolean,
    val longClickable: Boolean,
    val password: Boolean,
    val scrollable: Boolean,
    val accessibilityFocused: Boolean,
    val editable: Boolean,
    val canOpenPopup: Boolean,
    val dismissable: Boolean,
    val multiLine: Boolean,
    val contentInvalid: Boolean,
    val contextClickable: Boolean,
    val importance: Boolean,
    val showingHintText: Boolean,

    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,

    val width: Int,
    val height: Int,

    val index: Int,
    val depth: Int,
    val childCount: Int,
) {
    companion object {
        /**
         * 不要在多线程中使用
         */
        private val rect = Rect()
        fun info2data(
            node: AccessibilityNodeInfo,
        ): AttrInfo {
            node.getBoundsInScreen(rect)
            return AttrInfo(
                id = node.viewIdResourceName,
                name = node.className?.toString(),
                text = node.text?.toString(),
                desc = node.contentDescription?.toString(),
                hint = node.hintText?.toString(),
                error = node.error?.toString(),
                inputType = node.inputType,
                liveRegion = node.liveRegion,

                enabled = node.isEnabled,
                clickable = node.isClickable,
                checked = node.isChecked,
                checkable = node.isCheckable,
                focused = node.isFocused,
                focusable = node.isFocusable,
                visibleToUser = node.isVisibleToUser,
                selected = node.isSelected,
                longClickable = node.isLongClickable,
                password = node.isPassword,
                scrollable = node.isScrollable,
                accessibilityFocused = node.isAccessibilityFocused,
                editable = node.isEditable,
                canOpenPopup = node.canOpenPopup(),
                dismissable = node.isDismissable,
                multiLine = node.isMultiLine,
                contentInvalid = node.isContentInvalid,
                contextClickable = node.isContextClickable,
                importance = node.isImportantForAccessibility,
                showingHintText = node.isShowingHintText,

                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,

                width = rect.width(),
                height = rect.height(),

                index = node.getIndex(),
                depth = node.getDepth(),
                childCount = node.childCount,
            )
        }
    }
}
