package li.songe.selector


data class LogicalExpression(
    override val start: Int,
    override val end: Int,
    val left: Expression,
    val operator: PositionImpl<LogicalOperator>,
    val right: Expression,
) : Expression() {
    override fun <T> match(node: T, transform: Transform<T>): Boolean {
        return operator.value.compare(node, transform, left, right)
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
}