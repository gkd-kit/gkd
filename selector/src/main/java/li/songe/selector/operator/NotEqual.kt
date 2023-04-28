package li.songe.selector.operator

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.expression.BinaryExpression
import li.songe.selector.getDepth
import li.songe.selector.getIndex


object NotEqual : Operator("!=") {
    override fun match(expression: BinaryExpression): (nodeInfo: AccessibilityNodeInfo) -> Boolean {
        val value = expression.value
        return when (expression.name) {
            "id" -> when (value) {
                is String? -> ({ nodeInfo -> nodeInfo.viewIdResourceName?.toString() != value })
                else -> ({ false })
            }

            "index" -> when (value) {
                is Int? -> ({ nodeInfo -> nodeInfo.getIndex() != value })
                else -> ({ false })
            }

            "childCount" -> when (value) {
                is Int? -> ({ nodeInfo -> nodeInfo.childCount != value })
                else -> ({ false })
            }

            "depth" -> when (value) {
                is Int? -> ({ nodeInfo -> nodeInfo.getDepth() != value })
                else -> ({ false })
            }

            "text" -> when (value) {
                is String? -> ({ nodeInfo -> nodeInfo.text?.toString() != value })
                else -> ({ false })
            }

            "text_length" -> when (value) {
                is Int? -> ({ nodeInfo -> nodeInfo.text?.length != value })
                else -> ({ false })
            }

            "hint" -> when (value) {
                is String? -> ({ nodeInfo -> nodeInfo.hintText?.toString() != value })
                else -> ({ false })
            }

            "hint_length" -> when (value) {
                is Int? -> ({ nodeInfo -> nodeInfo.hintText?.length != value })
                else -> ({ false })
            }

            "desc" -> when (value) {
                is String? -> ({ nodeInfo -> nodeInfo.contentDescription?.toString() != value })
                else -> ({ false })
            }

            "desc_length" -> when (value) {
                is Int? -> ({ nodeInfo -> nodeInfo.contentDescription?.length != value })
                else -> ({ false })
            }

            "isPassword" -> when (value) {
                is Boolean? -> ({ nodeInfo -> nodeInfo.isPassword != value })
                else -> ({ false })
            }

            "isChecked" -> when (value) {
                is Boolean? -> ({ nodeInfo -> nodeInfo.isChecked != value })
                else -> ({ false })
            }

            "className" -> when (value) {
                is String? -> ({ nodeInfo -> nodeInfo.className?.toString() != value })
                else -> ({ false })
            }

            else -> ({ false })
        }
    }
}