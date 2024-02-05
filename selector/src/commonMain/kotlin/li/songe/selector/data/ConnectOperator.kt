package li.songe.selector.data

import li.songe.selector.Transform

sealed class ConnectOperator(val key: String) {
    override fun toString() = key
    abstract fun <T> traversal(node: T, transform: Transform<T>): Sequence<T?>
    abstract fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T?

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                BeforeBrother, AfterBrother, Ancestor, Child, Descendant
            ).sortedBy { -it.key.length }
        }
    }

    /**
     * A + B, 1,2,3,A,B,7,8
     */
    data object BeforeBrother : ConnectOperator("+") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getBeforeBrothers(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getBeforeBrother(node, offset)
    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    data object AfterBrother : ConnectOperator("-") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getAfterBrothers(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getAfterBrother(node, offset)
    }

    /**
     * A > B, A is the ancestor of B
     */
    data object Ancestor : ConnectOperator(">") {
        override fun <T> traversal(node: T, transform: Transform<T>) = transform.getAncestors(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getAncestor(node, offset)
    }

    /**
     * A < B, A is the child of B
     */
    data object Child : ConnectOperator("<") {
        override fun <T> traversal(node: T, transform: Transform<T>) = transform.getChildren(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getChild(node, offset)
    }

    /**
     * A << B, A is the descendant of B
     */
    data object Descendant : ConnectOperator("<<") {
        override fun <T> traversal(node: T, transform: Transform<T>) =
            transform.getDescendants(node)

        override fun <T> traversal(node: T, transform: Transform<T>, offset: Int): T? =
            transform.getDescendants(node).elementAtOrNull(offset)
    }


}
