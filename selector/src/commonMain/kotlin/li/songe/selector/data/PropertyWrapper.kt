package li.songe.selector.data

import li.songe.selector.Transform

data class PropertyWrapper(
    val propertySegment: PropertySegment,
    val to: ConnectWrapper? = null,
) {
    override fun toString(): String {
        return (if (to != null) {
            to.toString() + "\u0020"
        } else {
            ""
        }) + propertySegment.toString()
    }

    fun <T> matchTracks(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T>,
    ): List<T>? {
        if (!propertySegment.match(node, transform)) {
            return null
        }
        trackNodes.add(node)
        if (to == null) {
            return trackNodes
        }
        val r = to.matchTracks(node, transform, trackNodes)
        if (r == null) {
            trackNodes.removeLast()
        }
        return r
    }
}
