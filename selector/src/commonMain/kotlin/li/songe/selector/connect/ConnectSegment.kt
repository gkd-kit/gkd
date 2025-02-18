package li.songe.selector.connect

import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
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

    internal fun <T> traversal(context: QueryContext<T>, transform: Transform<T>): Sequence<T?> {
        return operator.traversal(context, transform, connectExpression)
    }

    val isMatchAnyAncestor = operator == ConnectOperator.Ancestor
            && connectExpression is PolynomialExpression
            && connectExpression.a == 1 && connectExpression.b == 0

    val isMatchAnyDescendant = operator == ConnectOperator.Descendant
            && connectExpression is PolynomialExpression
            && connectExpression.a == 1 && connectExpression.b == 0
}
