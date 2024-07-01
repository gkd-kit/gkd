package li.songe.selector

data class ConnectSegment(
    val operator: ConnectOperator = ConnectOperator.Ancestor,
    val connectExpression: ConnectExpression = PolynomialExpression(),
) {
    override fun toString(): String {
        if (operator == ConnectOperator.Ancestor && connectExpression is PolynomialExpression && connectExpression.a == 1 && connectExpression.b == 0) {
            return ""
        }
        return operator.stringify() + connectExpression.toString()
    }

    internal fun <T> traversal(node: T, transform: Transform<T>): Sequence<T?> {
        return operator.traversal(node, transform, connectExpression)
    }
}
