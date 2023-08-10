package li.songe.selector.data

import li.songe.selector.NodeMatchFc
import li.songe.selector.Transform


data class PropertySegment(
    /**
     * 此属性选择器是否被 @ 标记
     */
    val tracked: Boolean,
    val name: String,
    val expressions: List<Expression>,
) {
    override fun toString(): String {
        val matchTag = if (tracked) "@" else ""
        return matchTag + name + expressions.joinToString("") { "[$it]" }
    }

    private val matchName = if (name.isBlank() || name == "*") {
        object : NodeMatchFc {
            override fun <T> invoke(node: T, transform: Transform<T>) = true
        }
    } else {
        object : NodeMatchFc {
            override fun <T> invoke(node: T, transform: Transform<T>): Boolean {
                val str = transform.getName(node) ?: return false
                return (str.contentEquals(name) || (str.endsWith(name) && str[str.length - name.length - 1] == '.'))
            }
        }
    }

    fun <T> match(node: T, transform: Transform<T>): Boolean {
        return matchName(node, transform) && expressions.all { ex -> ex.match(node, transform) }
    }

}






