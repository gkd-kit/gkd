package li.songe.selector_core.data

import li.songe.selector_core.Node

sealed class ConnectOperator(val key: String) {
    override fun toString() = key
    abstract fun traversal(node: Node): Sequence<Node?>
    abstract fun traversal(node: Node, offset: Int): Node?

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
        override fun traversal(node: Node) = node.beforeBrothers
        override fun traversal(node: Node, offset: Int): Node? = node.getBeforeBrother(offset)
    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    object AfterBrother : ConnectOperator("-") {
        override fun traversal(node: Node) = node.afterBrothers
        override fun traversal(node: Node, offset: Int): Node? = node.getAfterBrother(offset)
    }

    /**
     * A > B, A is the ancestor of B
     */
    object Ancestor : ConnectOperator(">") {
        override fun traversal(node: Node) = node.ancestors
        override fun traversal(node: Node, offset: Int): Node? = node.getAncestor(offset)
    }

    /**
     * A < B, A is the child of B
     */
    object Child : ConnectOperator("<") {
        override fun traversal(node: Node) = node.children
        override fun traversal(node: Node, offset: Int): Node? = node.getChild(offset)
    }
}
