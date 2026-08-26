package li.gkd.selector.property

import li.gkd.selector.MatchOption
import li.gkd.selector.QueryContext
import li.gkd.selector.Stringify
import li.gkd.selector.Transform
import li.gkd.selector.connect.ConnectWrapper
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
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption,
    ): QueryContext<T> {
        if (!segment.match(context, transform)) {
            return context.mismatch()
        }
        if (to == null) {
            return context
        }
        return to.matchContext(context, transform, option)
    }

    val isMatchRoot = segment.units.any {
        val e = it.expression
        e is BinaryExpression && e.operator == CompareOperator.Equal && when {
            // null == Identifier(name="parent")
            e.right.value == null && e.left.value == "parent" -> true
            e.left.value == null && e.right.value == "parent" -> true
            else -> false
        }
    }

    val fastQueryList by lazy { segment.fastQueryList ?: emptyList() }

    val length: Int
        get() = if (to == null) 1 else to.to.length + 1
}
