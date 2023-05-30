package li.songe.selector_core.data

import li.songe.selector_core.NodeExt

data class PropertySegment(
    /**
     * 此属性选择器是否被 @ 标记
     */
    val tracked: Boolean,
    val name: String,
    val expressions: List<BinaryExpression>,
) {
    override fun toString(): String {
        val matchTag = if (tracked) "@" else ""
        return matchTag + name + expressions.joinToString("")
    }

    val matchName: (node: NodeExt) -> Boolean =
        if (name.isBlank() || name == "*")
            ({ true })
        else ({ node ->
            val str = node.name
            str.contentEquals(name) ||
                    (str.endsWith(name) && str[str.length - name.length - 1] == '.')
        })

    val matchExpressions: (node: NodeExt) -> Boolean = { node ->
        expressions.all { ex -> ex.match(node) }
    }

    fun match(node: NodeExt): Boolean {
        return matchName(node) && matchExpressions(node)
    }

}
