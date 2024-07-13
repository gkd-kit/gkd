package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class LogicalExpression(
    override val start: Int,
    override val end: Int,
    val left: Expression,
    val operator: PositionImpl<LogicalOperator>,
    val right: Expression,
) : Expression() {
    override fun <T> match(
        context: Context<T>,
        transform: Transform<T>,
    ): Boolean {
        return operator.value.compare(context, transform, left, right)
    }

    override val binaryExpressions
        get() = left.binaryExpressions + right.binaryExpressions

    override fun stringify(): String {
        val leftStr = if (left is LogicalExpression && left.operator.value != operator.value) {
            "(${left.stringify()})"
        } else {
            left.stringify()
        }
        val rightStr = if (right is LogicalExpression && right.operator.value != operator.value) {
            "(${right.stringify()})"
        } else {
            right.stringify()
        }
        return "$leftStr\u0020${operator.stringify()}\u0020$rightStr"
    }

    fun getSameExpressionArray(): Array<BinaryExpression>? {
        if (left is LogicalExpression && left.operator.value != operator.value) {
            return null
        }
        if (right is LogicalExpression && right.operator.value != operator.value) {
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