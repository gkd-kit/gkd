package li.songe.selector_core.data

import li.songe.selector_core.NodeExt

sealed class ConnectOperator(val key: String) {
    override fun toString() = key
    abstract fun traversal(node: NodeExt): Sequence<NodeExt?>
    abstract fun traversal(node: NodeExt, offset: Int): NodeExt?

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
        override fun traversal(node: NodeExt) = node.beforeBrothers
        override fun traversal(node: NodeExt, offset: Int): NodeExt? = node.getBeforeBrother(offset)
    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    object AfterBrother : ConnectOperator("-") {
        override fun traversal(node: NodeExt) = node.afterBrothers
        override fun traversal(node: NodeExt, offset: Int): NodeExt? = node.getAfterBrother(offset)
    }

    /**
     * A > B, A is the ancestor of B
     */
    object Ancestor : ConnectOperator(">") {
        override fun traversal(node: NodeExt) = node.ancestors
        override fun traversal(node: NodeExt, offset: Int): NodeExt? = node.getAncestor(offset)
    }

    /**
     * A < B, A is the child of B
     */
    object Child : ConnectOperator("<") {
        override fun traversal(node: NodeExt) = node.children
        override fun traversal(node: NodeExt, offset: Int): NodeExt? = node.getChild(offset)
    }
}
