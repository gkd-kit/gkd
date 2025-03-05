package li.songe.selector.connect

import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import kotlin.js.JsExport

@JsExport
sealed class ConnectOperator(val key: String) : Stringify {
    override fun stringify() = key
    fun formatOffset(offset: Int): String {
        require(offset >= 0)
        val n = offset + 1
        return if (n == 1) {
            key
        } else {
            key + n
        }
    }

    internal abstract fun <T> traversal(
        context: QueryContext<T>,
        transform: Transform<T>,
        connectExpression: ConnectExpression
    ): Sequence<T>

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                BeforeBrother, AfterBrother, Ancestor, Child, Descendant, Previous
            ).sortedBy { -it.key.length }
        }
    }

    /**
     * A + B, 1,2,3,A,B,7,8
     */
    data object BeforeBrother : ConnectOperator("+") {
        override fun <T> traversal(
            context: QueryContext<T>, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.traverseBeforeBrothers(context.current, connectExpression)

    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    data object AfterBrother : ConnectOperator("-") {
        override fun <T> traversal(
            context: QueryContext<T>, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.traverseAfterBrothers(context.current, connectExpression)
    }

    /**
     * A > B, A is the ancestor of B
     */
    data object Ancestor : ConnectOperator(">") {
        override fun <T> traversal(
            context: QueryContext<T>, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.traverseAncestors(context.current, connectExpression)

    }

    /**
     * A < B, A is the child of B
     */
    data object Child : ConnectOperator("<") {
        override fun <T> traversal(
            context: QueryContext<T>, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.traverseChildren(context.current, connectExpression)
    }

    /**
     * A << B, A is the descendant of B
     */
    data object Descendant : ConnectOperator("<<") {
        override fun <T> traversal(
            context: QueryContext<T>, transform: Transform<T>, connectExpression: ConnectExpression
        ) = transform.traverseDescendants(context.current, connectExpression)
    }

    /**
     * A -> B + C, A is the context previous node of B, A==C
     * A ->2 B + C + D, A==D
     */
    data object Previous : ConnectOperator("->") {
        override fun <T> traversal(
            context: QueryContext<T>, transform: Transform<T>, connectExpression: ConnectExpression
        ) = sequence<T> {
            var prev = context.getPrev(connectExpression.minOffset)
            var offset = connectExpression.minOffset
            while (prev != null) {
                if (connectExpression.checkOffset(offset)) {
                    yield(prev.current)
                }
                prev = prev.prev
                offset++
                if (connectExpression.maxOffset?.let { offset > it } == true) {
                    break
                }
            }
        }
    }

}
