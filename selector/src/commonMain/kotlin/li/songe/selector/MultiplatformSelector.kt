package li.songe.selector

import kotlin.js.JsExport

@JsExport
@Suppress("UNUSED", "UNCHECKED_CAST")
class MultiplatformSelector private constructor(
    internal val selector: Selector,
) {
    val tracks = selector.tracks
    val trackIndex = selector.trackIndex
    val connectKeys = selector.connectKeys

    val qfIdValue = selector.qfIdValue
    val qfVidValue = selector.qfVidValue
    val qfTextValue = selector.qfTextValue
    val canQf = selector.canQf
    val isMatchRoot = selector.isMatchRoot

    val binaryExpressions = selector.binaryExpressions
    fun checkType(typeInfo: TypeInfo) = selector.checkType(typeInfo)

    fun <T : Any> match(node: T, transform: MultiplatformTransform<T>): T? {
        return selector.match(node, transform.transform)
    }

    fun <T : Any> matchTrack(node: T, transform: MultiplatformTransform<T>): Array<T>? {
        return selector.matchTracks(node, transform.transform)?.toTypedArray<Any?>() as Array<T>?
    }

    override fun toString() = selector.toString()

    companion object {
        fun parse(source: String) = MultiplatformSelector(Selector.parse(source))
        fun parseOrNull(source: String) = Selector.parseOrNull(source)?.let(::MultiplatformSelector)
    }
}

