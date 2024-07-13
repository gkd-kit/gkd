package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class PropertyWrapper(
    val segment: PropertySegment,
    val to: ConnectWrapper? = null,
) : Stringify {
    override fun stringify(): String {
        return (if (to != null) {
            to.stringify() + "\u0020"
        } else {
            ""
        }) + segment.stringify()
    }

    fun <T> matchContext(
        context: Context<T>,
        transform: Transform<T>,
        option: MatchOption,
    ): Context<T>? {
        if (!segment.match(context, transform)) {
            return null
        }
        if (to == null) {
            return context
        }
        return to.matchContext(context, transform, option)
    }


    val isMatchRoot = segment.expressions.any { e ->
        e is BinaryExpression && e.operator.value == CompareOperator.Equal && ((e.left.value == "depth" && e.right.value == 0) || (e.left.value == "parent" && e.right.value == "null"))
    }

    val fastQueryList = getFastQueryList(segment) ?: emptyList()

    val length: Int
        get() = if (to == null) 1 else to.to.length + 1
}
