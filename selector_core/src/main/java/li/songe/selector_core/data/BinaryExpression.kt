package li.songe.selector_core.data

import li.songe.selector_core.NodeExt

data class BinaryExpression(val name: String, val operator: CompareOperator, val value: Any?) {
    fun match(node: NodeExt) = operator.compare(node.attr(name), value)
    override fun toString() = "[${name}${operator}${
        if (value is String) {
            "`${value.replace("`", "\\`")}`"
        } else {
            value
        }
    }]"
}
