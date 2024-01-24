package li.songe.selector

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


@OptIn(ExperimentalJsExport::class)
@JsExport
class CommonSelector private constructor(
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

    fun <T : Any> match(node: T, transform: CommonTransform<T>): T? {
        return selector.match(node, transform.transform)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> matchTrack(node: T, transform: CommonTransform<T>): Array<T>? {
        return selector.matchTracks(node, transform.transform)?.toTypedArray<Any?>() as Array<T>?
    }

    override fun toString() = selector.toString()

    companion object {
        fun parse(source: String) = CommonSelector(Selector.parse(source))
        fun parseOrNull(source: String) = Selector.parseOrNull(source)?.let(::CommonSelector)
    }
}

