package li.songe.selector.data

import li.songe.selector.Transform
import li.songe.selector.NodeTraversalFc

data class ConnectSegment(
    val operator: ConnectOperator = ConnectOperator.Ancestor,
    val connectExpression: ConnectExpression = PolynomialExpression(),
) {
    override fun toString(): String {
        if (operator == ConnectOperator.Ancestor && connectExpression is PolynomialExpression && connectExpression.a == 1 && connectExpression.b == 0) {
            return ""
        }
        return operator.toString() + connectExpression.toString()
    }

    internal val traversal = if (connectExpression.isConstant) {
        object : NodeTraversalFc {
            override fun <T> invoke(node: T, transform: Transform<T>): Sequence<T?> = sequence {
                val node1 = operator.traversal(node, transform, connectExpression.minOffset)
                if (node1 != null) {
                    yield(node1)
                }
            }
        }
    } else {
        object : NodeTraversalFc {
            override fun <T> invoke(node: T, transform: Transform<T>): Sequence<T?> {
                return connectExpression.traversal(
                    operator.traversal(node, transform)
                )
            }
        }
    }

}
