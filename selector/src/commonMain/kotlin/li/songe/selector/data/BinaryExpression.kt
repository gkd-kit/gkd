package li.songe.selector.data

import li.songe.selector.Transform

data class BinaryExpression(
    val name: String,
    val operator: CompareOperator,
    val value: PrimitiveValue
) :
    Expression() {
    override fun <T> match(node: T, transform: Transform<T>) =
        operator.compare(transform.getAttr(node, name), value.value)

    override val binaryExpressions = listOf(this)

    override fun toString() = "${name}${operator.key}${value}"
}
