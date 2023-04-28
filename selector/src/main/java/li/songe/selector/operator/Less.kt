package li.songe.selector.operator

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.expression.BinaryExpression
import li.songe.selector.getDepth
import li.songe.selector.getIndex

object Less : Operator("<") {
    override fun match(expression: BinaryExpression): (nodeInfo: AccessibilityNodeInfo) -> Boolean {
        val value = expression.value
        return when (expression.name) {
            "index" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.getIndex()?.let { it < value } == true })
                else -> ({ false })
            }

            "childCount" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.childCount < value })
                else -> ({ false })
            }

            "depth" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.getDepth() < value })
                else -> ({ false })
            }

            "text_length" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.text?.length?.let { it < value } == true })
                else -> ({ false })
            }

            "hint_length" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.hintText?.length?.let { it < value } == true })
                else -> ({ false })
            }

            "desc_length" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.contentDescription?.length?.let { it < value } == true })
                else -> ({ false })
            }

            "className_length" -> when (value) {
                is Int -> ({ nodeInfo -> nodeInfo.className?.length?.let { it < value } == true })
                else -> ({ false })
            }

            else -> ({ false })
        }
    }
}