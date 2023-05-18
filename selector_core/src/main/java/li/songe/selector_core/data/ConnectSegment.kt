package li.songe.selector_core.data

import li.songe.selector_core.Node

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

    fun traversal(node: Node): Sequence<Node?> {
        if (polynomialExpression.isConstant) {
            return sequence {
                val node1 = operator.traversal(node, polynomialExpression.b1)
                if (node1 != null) {
                    yield(node1)
                }
            }
        }
        return polynomialExpression.traversal(operator.traversal(node))
    }
}
