package li.songe.selector

sealed class Expression : Position {
    internal abstract fun <T> match(node: T, transform: Transform<T>): Boolean

    abstract val binaryExpressions: Array<BinaryExpression>
}
