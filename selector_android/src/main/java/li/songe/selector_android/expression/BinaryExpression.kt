package li.songe.selector_android.expression

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector_android.operator.Operator

data class BinaryExpression(val name: String, val operator: Operator, val value: Any?) {

    override fun toString() = "[${name}${operator}${
        if (value is String) {
            "`${value.replace("`", "\\`")}`"
        } else {
            value
        }
    }]"

    val matchNodeInfo: (nodeInfo: AccessibilityNodeInfo) -> Boolean = operator.match(this)
}
