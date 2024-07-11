package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class ConnectSegment(
    val operator: ConnectOperator = ConnectOperator.Ancestor,
    val connectExpression: ConnectExpression = PolynomialExpression(),
) : Stringify {
    override fun stringify(): String {
        if (isMatchAnyAncestor) {
            return ""
        }
        return operator.stringify() + connectExpression.stringify()
    }

    internal fun <T> traversal(node: T, transform: Transform<T>): Sequence<T?> {
        return operator.traversal(node, transform, connectExpression)
    }

    val isMatchAnyAncestor = operator == ConnectOperator.Ancestor
            && connectExpression is PolynomialExpression
            && connectExpression.a == 1 && connectExpression.b == 0

    val isMatchAnyDescendant = operator == ConnectOperator.Descendant
            && connectExpression is PolynomialExpression
            && connectExpression.a == 1 && connectExpression.b == 0
}
