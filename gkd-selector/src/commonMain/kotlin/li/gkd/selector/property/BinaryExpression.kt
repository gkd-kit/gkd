package li.gkd.selector.property

import li.gkd.selector.QueryContext
import li.gkd.selector.Transform
import kotlin.js.JsExport

@JsExport
data class BinaryExpression(
    val left: ValueExpression,
    val operator: CompareOperator,
    val right: ValueExpression,
) : Expression() {
    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
    ): Boolean {
        return operator.compare(context, transform, left, right)
    }

    override fun getBinaryExpressionList() = arrayOf(this)

    override fun stringify() = "${left.stringify()}${operator.stringify()}${right.stringify()}"

    val properties: Array<String>
        get() = arrayOf(*left.properties, *right.properties)

    val methods: Array<String>
        get() = arrayOf(*left.methods, *right.methods)
}
