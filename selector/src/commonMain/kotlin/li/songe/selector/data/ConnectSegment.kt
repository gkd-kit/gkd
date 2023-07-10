package li.songe.selector.data

import li.songe.selector.Transform
import li.songe.selector.NodeTraversalFc

data class ConnectSegment(
    val operator: ConnectOperator = ConnectOperator.Ancestor,
    val polynomialExpression: PolynomialExpression = PolynomialExpression()
) {
    override fun toString(): String {
        if (operator == ConnectOperator.Ancestor && polynomialExpression.a == 1 && polynomialExpression.b == 0) {
            return ""
        }
        return operator.toString() + polynomialExpression.toString()
    }

    internal val traversal = if (polynomialExpression.isConstant) {
        object : NodeTraversalFc {
            override fun <T> invoke(node: T, transform: Transform<T>): Sequence<T?> = sequence {
                val node1 = operator.traversal(node, transform, polynomialExpression.b1)
                if (node1 != null) {
                    yield(node1)
                }
            }
        }
    } else {
        object : NodeTraversalFc {
            override fun <T> invoke(node: T, transform: Transform<T>): Sequence<T?> {
                return polynomialExpression.traversal(
                    operator.traversal(node, transform)
                )
            }
        }
    }

}
