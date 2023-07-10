package li.songe.selector.data

import li.songe.selector.Transform

sealed class ConnectOperator(val key: String) {
    override fun toString() = key
    abstract fun <T> traversal(node: T, transform: Transform<T>): Sequence<T?>
    abstract fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T?

    companion object {
        val allSubClasses = listOf(
            BeforeBrother,
            AfterBrother,
            Ancestor,
            Child
        ).sortedBy { -it.key.length }
    }

    /**
     * A + B, 1,2,3,A,B,7,8
     */
    object BeforeBrother : ConnectOperator("+") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getBeforeBrothers(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getBeforeBrother(node, offset)
    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    object AfterBrother : ConnectOperator("-") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getAfterBrothers(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getAfterBrother(node, offset)
    }

    /**
     * A > B, A is the ancestor of B
     */
    object Ancestor : ConnectOperator(">") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getAncestors(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getAncestor(node, offset)
    }

    /**
     * A < B, A is the child of B
     */
    object Child : ConnectOperator("<") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getChildren(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getChild(node, offset)
    }
}
