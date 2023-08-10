package li.songe.selector.data

import li.songe.selector.Transform

sealed class LogicalOperator(val key: String) {
    companion object {
        val allSubClasses = listOf(
            AndOperator, OrOperator
        ).sortedBy { -it.key.length }
    }

    override fun toString() = key
    abstract fun <T> compare(
        node: T,
        transform: Transform<T>,
        left: Expression,
        right: Expression,
    ): Boolean

    object AndOperator : LogicalOperator("&&") {
        override fun <T> compare(
            node: T,
            transform: Transform<T>,
            left: Expression,
            right: Expression,
        ): Boolean {
            return left.match(node, transform) && right.match(node, transform)
        }
    }

    object OrOperator : LogicalOperator("||") {
        override fun <T> compare(
            node: T,
            transform: Transform<T>,
            left: Expression,
            right: Expression,
        ): Boolean {
            return left.match(node, transform) || right.match(node, transform)
        }
    }
}
