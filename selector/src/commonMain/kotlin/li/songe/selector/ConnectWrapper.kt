package li.songe.selector

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
        context: Context<T>,
        transform: Transform<T>,
        option: MatchOption
    ): Context<T>? {
        if (isMatchRoot) {
            // C <<n [parent=null] >n A
            val root = transform.getRoot(context.current) ?: return null
            return to.matchContext(context.next(root), transform, option)
        }
        if (canFq && option.fastQuery) {
            // C[name='a'||vid='b'] <<n A
            transform.traverseFastQueryDescendants(context.current, to.fastQueryList).forEach {
                val r = to.matchContext(context.next(it), transform, option)
                if (r != null) return r
            }
        } else {
            segment.traversal(context.current, transform).forEach {
                if (it == null) return@forEach
                val r = to.matchContext(context.next(it), transform, option)
                if (r != null) return r
            }
        }
        return null
    }

    private val isMatchRoot = to.isMatchRoot && segment.isMatchAnyAncestor
    val canFq = segment.isMatchAnyDescendant && to.fastQueryList.isNotEmpty()
}