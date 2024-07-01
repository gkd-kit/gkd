package li.songe.selector

data class NotExpression(
    override val start: Int,
    val expression: Expression
) : Expression() {
    override val end: Int
        get() = expression.end

    override fun <T> match(node: T, transform: Transform<T>): Boolean {
        return !expression.match(node, transform)
    }

    override val binaryExpressions: Array<BinaryExpression>
        get() = expression.binaryExpressions

    override fun stringify(): String {
        return "!(${expression.stringify()})"
    }
}
