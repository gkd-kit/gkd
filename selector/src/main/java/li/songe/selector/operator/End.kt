package li.songe.selector.operator

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.expression.BinaryExpression


object End : Operator("$=") {
    override fun match(expression: BinaryExpression): (nodeInfo: AccessibilityNodeInfo) -> Boolean {
        val value = expression.value
        return when (expression.name) {
            "id" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.viewIdResourceName?.endsWith(value) == true })
                else -> ({ false })
            }

            "text" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.text?.endsWith(value) == true })
                else -> ({ false })
            }

            "hint" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.hintText?.endsWith(value) == true })
                else -> ({ false })
            }

            "desc" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.contentDescription?.endsWith(value) == true })
                else -> ({ false })
            }

            "className" -> when (value) {
                is String -> ({ nodeInfo -> nodeInfo.className?.endsWith(value) == true })
                else -> ({ false })
            }

            else -> ({ false })
        }
    }
}