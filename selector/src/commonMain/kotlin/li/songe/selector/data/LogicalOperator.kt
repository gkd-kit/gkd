package li.songe.selector.data

import li.songe.selector.Transform

sealed class LogicalOperator(val key: String) {
    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                AndOperator, OrOperator
            ).sortedBy { -it.key.length }
        }
    }

    abstract fun <T> compare(
        node: T,
        transform: Transform<T>,
        left: Expression,
        right: Expression,
    ): Boolean

    data object AndOperator : LogicalOperator("&&") {
        override fun <T> compare(
            node: T,
            transform: Transform<T>,
            left: Expression,
            right: Expression,
        ): Boolean {
            return left.match(node, transform) && right.match(node, transform)
        }
    }

    data object OrOperator : LogicalOperator("||") {
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
