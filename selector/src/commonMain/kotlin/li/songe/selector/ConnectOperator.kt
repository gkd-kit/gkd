package li.songe.selector

sealed class ConnectOperator(val key: String) : Stringify {
    override fun stringify() = key

    internal abstract fun <T> traversal(
        node: T, transform: Transform<T>, connectExpression: ConnectExpression
    ): Sequence<T>

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
        override fun <T> traversal(
            node: T, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.getBeforeBrothers(node, connectExpression)

    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    data object AfterBrother : ConnectOperator("-") {
        override fun <T> traversal(
            node: T, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.getAfterBrothers(node, connectExpression)
    }

    /**
     * A > B, A is the ancestor of B
     */
    data object Ancestor : ConnectOperator(">") {
        override fun <T> traversal(
            node: T, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.getAncestors(node, connectExpression)

    }

    /**
     * A < B, A is the child of B
     */
    data object Child : ConnectOperator("<") {
        override fun <T> traversal(
            node: T, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.getChildrenX(node, connectExpression)
    }

    /**
     * A << B, A is the descendant of B
     */
    data object Descendant : ConnectOperator("<<") {
        override fun <T> traversal(
            node: T, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.getDescendantsX(node, connectExpression)
    }

}
