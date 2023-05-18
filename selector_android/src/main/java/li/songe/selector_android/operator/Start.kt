package li.songe.selector_android.operator

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector_android.expression.BinaryExpression


object Start : Operator("^=") {
    override fun match(expression: BinaryExpression): (nodeInfo: AccessibilityNodeInfo) -> Boolean {
        val value = expression.value
        return when (expression.name) {
            "id" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.viewIdResourceName?.startsWith(value) == true })
                else -> ({ false })
            }

            "text" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.text?.startsWith(value) == true })
                else -> ({ false })
            }

            "hint" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.hintText?.startsWith(value) == true })
                else -> ({ false })
            }

            "desc" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.contentDescription?.startsWith(value) == true })
                else -> ({ false })
            }

            "className" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.className?.startsWith(value) == true })
                else -> ({ false })
            }

            else -> ({ false })
        }
    }

}