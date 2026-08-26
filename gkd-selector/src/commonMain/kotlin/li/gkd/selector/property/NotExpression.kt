package li.gkd.selector.property

import li.gkd.selector.QueryContext
import li.gkd.selector.Transform
import kotlin.js.JsExport

@JsExport
data class NotExpression(
    val expression: Expression
) : Expression() {

    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Boolean {
        return !expression.match(context, transform)
    }

    override fun getBinaryExpressionList() = expression.getBinaryExpressionList()

    override fun stringify(): String {
        return "!(${expression.stringify()})"
    }
}
