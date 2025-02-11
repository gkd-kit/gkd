package li.songe.selector

import kotlin.js.JsExport

@JsExport
sealed class FastQuery(open val value: String) : Stringify {
    override fun stringify() = value
    data class Id(override val value: String) : FastQuery(value)
    data class Vid(override val value: String) : FastQuery(value)
    data class Text(override val value: String) : FastQuery(value)
}
