package li.songe.selector.data

import li.songe.selector.Transform

data class ConnectWrapper(
    val connectSegment: ConnectSegment,
    val to: PropertyWrapper,
) {
    override fun toString(): String {
        return (to.toString() + "\u0020" + connectSegment.toString()).trim()
    }

    fun <T> matchTracks(
        node: T, transform: Transform<T>,
        trackNodes: MutableList<T>,
    ): List<T>? {
        connectSegment.traversal(node, transform).forEach {
            if (it == null) return@forEach
            val r = to.matchTracks(it, transform, trackNodes)
            if (r != null) return r
        }
        return null
    }
}