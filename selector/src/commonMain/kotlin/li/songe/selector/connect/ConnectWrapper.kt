package li.songe.selector.connect

import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import li.songe.selector.property.PropertyWrapper
import kotlin.js.JsExport

@JsExport
data class ConnectWrapper(
    val segment: ConnectSegment,
    val to: PropertyWrapper,
) : Stringify {
    override fun stringify(): String {
        return (to.stringify() + "\u0020" + segment.stringify()).trim()
    }

    fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ): QueryContext<T> {
        if (isMatchRoot) {
            // C <<n [parent=null] >n A
            val root = transform.getRoot(context.current) ?: return context.mismatch()
            return to.matchContext(context.next(root), transform, option)
        }
        if (canFq && option.fastQuery) {
            // C[name='a'||vid='b'] <<n A
            transform.traverseFastQueryDescendants(context.current, to.fastQueryList).forEach {
                val r = to.matchContext(context.next(it), transform, option)
                if (r.matched) return r
            }
        } else {
            segment.traversal(context.current, transform).forEach {
                if (it == null) return@forEach
                val r = to.matchContext(context.next(it), transform, option)
                if (r.matched) return r
            }
        }
        return context.mismatch()
    }

    private val isMatchRoot = segment.isMatchAnyAncestor && to.isMatchRoot
    val canFq = segment.isMatchAnyDescendant && to.fastQueryList.isNotEmpty()
}