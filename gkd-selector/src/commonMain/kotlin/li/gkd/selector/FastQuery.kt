package li.gkd.selector

import li.gkd.selector.property.FastQueryOperator
import li.gkd.selector.property.compareFastQueryValue
import li.gkd.selector.property.comparePrimitiveValue
import kotlin.js.JsExport

@JsExport
public sealed class FastQuery(
    public open val value: String,
) {
    internal abstract val attributeName: String

    internal open fun acceptValue(candidate: Any?): Boolean = comparePrimitiveValue(candidate, value)

    public data class Id(override val value: String) : FastQuery(value) {
        override val attributeName: String
            get() = "id"
    }

    public data class Vid(override val value: String) : FastQuery(value) {
        override val attributeName: String
            get() = "vid"
    }

    public data class Text(
        override val value: String,
        val operator: FastQueryOperator,
    ) : FastQuery(value) {
        override val attributeName: String
            get() = "text"

        override fun acceptValue(candidate: Any?): Boolean =
            operator.compareFastQueryValue(candidate, value)
    }
}
