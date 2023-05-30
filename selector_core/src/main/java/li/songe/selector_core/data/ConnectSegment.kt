package li.songe.selector_core.data

import li.songe.selector_core.NodeExt

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

    val traversal: (node: NodeExt) -> Sequence<NodeExt?> = if (polynomialExpression.isConstant) {
        ({ node ->
            sequence {
                val node1 = operator.traversal(node, polynomialExpression.b1)
                if (node1 != null) {
                    yield(node1)
                }
            }
        })
    } else {
        ({ node -> polynomialExpression.traversal(operator.traversal(node)) })
    }

}
