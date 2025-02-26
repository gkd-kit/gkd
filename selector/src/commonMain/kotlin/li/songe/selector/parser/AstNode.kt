package li.songe.selector.parser

import li.songe.selector.Stringify
import kotlin.js.JsExport

@JsExport
data class AstNode<T : Any>(
    val start: Int,
    val end: Int,
    val value: T,
    val children: List<AstNode<Any>>,
) : Stringify {

    @Suppress("unused")
    val outChildren by lazy { children.toTypedArray() }

    val name: String
        get() = value::class.simpleName.toString()

    override fun stringify(): String {
        if (children.isEmpty()) {
            return "{name:\"$name\", range:[$start, $end]}"
        }
        return "{name:\"$name\", range:[$start, $end], children:${children.map { it.stringify() }}}"
    }

    fun sameRange(other: AstNode<Any>): Boolean {
        return start == other.start && end == other.end
    }
}
