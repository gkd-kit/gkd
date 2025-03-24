package li.songe.selector

import li.songe.selector.property.CompareOperator
import kotlin.js.JsExport

@JsExport
sealed class FastQuery(open val value: String) : Stringify {
    override fun stringify() = value
    open fun acceptText(text: String): Boolean = text == value

    data class Id(override val value: String) : FastQuery(value)
    data class Vid(override val value: String) : FastQuery(value)
    data class Text(override val value: String, val operator: CompareOperator) : FastQuery(value) {
        override fun acceptText(text: String): Boolean = when (operator) {
            CompareOperator.Equal -> text == value
            CompareOperator.Start -> text.startsWith(value)
            CompareOperator.Include -> text.contains(value)
            CompareOperator.End -> text.endsWith(value)
            else -> error("Invalid operator: $operator")
        }
    }
}
