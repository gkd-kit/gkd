package li.songe.selector.data

import li.songe.selector.Transform

sealed class Expression {
    internal abstract fun <T> match(node: T, transform: Transform<T>): Boolean

    abstract val binaryExpressions: Array<BinaryExpression>
}
