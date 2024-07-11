package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class PropertySegment(
    val at: Boolean,
    val name: String,
    val expressions: List<Expression>,
) : Stringify {
    private val matchAnyName = name.isBlank() || name == "*"

    val binaryExpressions
        get() = expressions.flatMap { it.binaryExpressions.toList() }.toTypedArray()

    override fun stringify(): String {
        val matchTag = if (at) "@" else ""
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

    fun <T> match(
        context: Context<T>,
        transform: Transform<T>,
    ): Boolean {
        return matchName(context.current, transform) && expressions.all { ex ->
            ex.match(
                context,
                transform
            )
        }
    }

}






