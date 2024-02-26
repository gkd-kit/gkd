package li.songe.selector.data

import li.songe.selector.Transform

data class LogicalExpression(
    val left: Expression,
    val operator: LogicalOperator,
    val right: Expression,
) : Expression() {
    override fun <T> match(node: T, transform: Transform<T>): Boolean {
        return operator.compare(node, transform, left, right)
    }

    override val binaryExpressions
        get() = left.binaryExpressions + right.binaryExpressions

    override fun toString(): String {
        val leftStr = if (left is LogicalExpression && left.operator != operator) {
            "($left)"
        } else {
            left.toString()
        }
        val rightStr = if (right is LogicalExpression && right.operator != operator) {
            "($right)"
        } else {
            right.toString()
        }
        return "$leftStr\u0020${operator.key}\u0020$rightStr"
    }
}