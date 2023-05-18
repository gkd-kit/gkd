package li.songe.selector_android.operator

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector_android.expression.BinaryExpression

sealed class Operator(private val key: String) {
    override fun toString() = key
    abstract fun match(expression: BinaryExpression): (nodeInfo: AccessibilityNodeInfo) -> Boolean
}