package li.songe.selector_core.data

import li.songe.selector_core.Node

data class PropertySegment(
    /**
     * 此属性选择器是否被 @ 标记
     */
    val match: Boolean,
    val name: String,
    val expressions: List<BinaryExpression>,
) {
    override fun toString(): String {
        val matchTag = if (match) "@" else ""
        return matchTag + name + expressions.joinToString("")
    }

    val matchName: (node: Node) -> Boolean =
        if (name.isBlank() || name == "*")
            ({ true })
        else ({ node ->
            val str = node.name
            str.contentEquals(name) ||
                    (str.endsWith(name) && str[str.length - name.length - 1] == '.')
        })

    val matchExpressions: (node: Node) -> Boolean = { node ->
        expressions.all { ex -> ex.match(node) }
    }

    fun match(node: Node): Boolean {
        return matchName(node) && matchExpressions(node)
    }

}
