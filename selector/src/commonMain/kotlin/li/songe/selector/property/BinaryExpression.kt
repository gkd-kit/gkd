package li.songe.selector.property

import li.songe.selector.QueryContext
import li.songe.selector.Transform
import li.songe.selector.connect.CompareOperator
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
