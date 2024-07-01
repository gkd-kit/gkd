package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class BinaryExpression(
    override val start: Int,
    override val end: Int,
    val left: ValueExpression,
    val operator: PositionImpl<CompareOperator>,
    val right: ValueExpression,
) : Expression() {
    override fun <T> match(node: T, transform: Transform<T>): Boolean {
        return operator.value.compare(node, transform, left, right)
    }

    override val binaryExpressions
        get() = arrayOf(this)

    override fun stringify() = "${left.stringify()}${operator.stringify()}${right.stringify()}"

    val properties: Array<String>
        get() = arrayOf(*left.properties, *right.properties)

    val methods: Array<String>
        get() = arrayOf(*left.methods, *right.methods)
}
