package li.songe.selector

import kotlin.js.JsExport

@JsExport
@Suppress("UNUSED")
class MultiplatformSelector private constructor(
    internal val selector: Selector,
) {
    val tracks = selector.tracks
    val trackIndex = selector.trackIndex
    val connectKeys = selector.connectKeys
    val propertyNames = selector.propertyNames

    val qfIdValue = selector.qfIdValue
    val qfVidValue = selector.qfVidValue
    val qfTextValue = selector.qfTextValue
    val canQf = selector.canQf
    val isMatchRoot = selector.isMatchRoot
    fun checkType(getType: (String) -> String): Boolean {
        return selector.checkType(getType)
    }

    fun <T : Any> match(node: T, transform: MultiplatformTransform<T>): T? {
        return selector.match(node, transform.transform)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> matchTrack(node: T, transform: MultiplatformTransform<T>): Array<T>? {
        return selector.matchTracks(node, transform.transform)?.toTypedArray<Any?>() as Array<T>?
    }

    override fun toString() = selector.toString()

    companion object {
        fun parse(source: String) = MultiplatformSelector(Selector.parse(source))
        fun parseOrNull(source: String) = Selector.parseOrNull(source)?.let(::MultiplatformSelector)
    }
}

