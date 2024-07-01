package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed interface Stringify {
    fun stringify(): String
}

sealed interface Position : Stringify {
    val start: Int
    val end: Int
    val length: Int
        get() = end - start
}

@JsExport
data class PositionImpl<T : Stringify>(
    override val start: Int,
    override val end: Int,
    val value: T
) : Position {
    override fun stringify(): String {
        return value.stringify()
    }
}