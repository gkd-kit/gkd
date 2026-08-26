package li.songe.selector.property

import li.songe.selector.QueryContext
import li.songe.selector.Transform
import kotlin.js.JsExport

@JsExport
data class LogicalExpression(
    val left: Expression,
    val operator: LogicalOperator,
    val right: Expression,
) : Expression() {
    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Boolean {
        return operator.compare(context, transform, left, right)
    }

    override fun getBinaryExpressionList() =
        left.getBinaryExpressionList() + right.getBinaryExpressionList()

    override fun stringify(): String {
        val leftStr = if (left is LogicalExpression && left.operator != operator) {
            "(${left.stringify()})"
        } else {
            left.stringify()
        }
        val rightStr = if (right is LogicalExpression && right.operator != operator) {
            "(${right.stringify()})"
        } else {
            right.stringify()
        }
        return "$leftStr\u0020${operator.stringify()}\u0020$rightStr"
    }

    fun getSameExpressionArray(): Array<BinaryExpression>? {
        if (left is LogicalExpression && left.operator != operator) {
            return null
        }
        if (right is LogicalExpression && right.operator != operator) {
            return null
        }
        return when (left) {
            is BinaryExpression -> when (right) {
                is BinaryExpression -> arrayOf(left, right)
                is LogicalExpression -> {
                    arrayOf(left) + (right.getSameExpressionArray() ?: return null)
                }

                is NotExpression -> null
            }

            is LogicalExpression -> {
                val leftArray = left.getSameExpressionArray() ?: return null
                when (right) {
                    is BinaryExpression -> leftArray + right
                    is LogicalExpression -> {
                        return leftArray + (right.getSameExpressionArray() ?: return null)
                    }

                    is NotExpression -> null
                }
            }

            is NotExpression -> null
        }
    }
}