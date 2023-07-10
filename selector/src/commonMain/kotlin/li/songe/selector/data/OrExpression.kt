package li.songe.selector.data

import li.songe.selector.Transform

data class OrExpression(val expressions: List<BinaryExpression>) {
    override fun toString(): String {
        if (expressions.isEmpty()) return ""
        return "[" + expressions.joinToString("||") + "]"
    }

    fun <T> match(node: T, transform: Transform<T>): Boolean {
        return expressions.any { ex -> ex.match(node, transform) }
    }
}
