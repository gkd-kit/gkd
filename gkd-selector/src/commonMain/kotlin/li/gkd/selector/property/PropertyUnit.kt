package li.gkd.selector.property

import li.gkd.selector.Stringify
import kotlin.js.JsExport

@JsExport
data class PropertyUnit(
    val expression: Expression
) : Stringify {
    override fun stringify(): String {
        return "[${expression.stringify()}]"
    }
}
