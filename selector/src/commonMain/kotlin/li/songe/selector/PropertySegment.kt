package li.songe.selector


data class PropertySegment(
    /**
     * 此属性选择器是否被 @ 标记
     */
    val tracked: Boolean,
    val name: String,
    val expressions: List<Expression>,
) {
    private val matchAnyName = name.isBlank() || name == "*"

    val binaryExpressions
        get() = expressions.flatMap { it.binaryExpressions.toList() }.toTypedArray()

    override fun toString(): String {
        val matchTag = if (tracked) "@" else ""
        return matchTag + name + expressions.joinToString("") { "[${it.stringify()}]" }
    }

    private fun <T> matchName(node: T, transform: Transform<T>): Boolean {
        if (matchAnyName) return true
        val str = transform.getName(node) ?: return false
        if (str.length == name.length) {
            return str.contentEquals(name)
        } else if (str.length > name.length) {
            return str[str.length - name.length - 1] == '.' && str.endsWith(name)
        }
        return false
    }

    fun <T> match(node: T, transform: Transform<T>): Boolean {
        return matchName(node, transform) && expressions.all { ex -> ex.match(node, transform) }
    }

}






