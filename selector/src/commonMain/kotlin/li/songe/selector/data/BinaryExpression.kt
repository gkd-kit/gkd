package li.songe.selector.data

import li.songe.selector.Transform

data class BinaryExpression(val name: String, val operator: CompareOperator, val value: Any?) {
    fun <T> match(node: T, transform: Transform<T>) =
        operator.compare(transform.getAttr(node, name), value)

    override fun toString() = "${name}${operator}${
        if (value is String) {
            "'${value.replace("'", "\\'")}'"
        } else {
            value
        }
    }"
}
