package li.songe.selector

data class PropertyWrapper(
    val segment: PropertySegment,
    val to: ConnectWrapper? = null,
) {
    override fun toString(): String {
        return (if (to != null) {
            to.toString() + "\u0020"
        } else {
            ""
        }) + segment.toString()
    }

    fun <T> matchTracks(
        node: T,
        transform: Transform<T>,
        trackNodes: MutableList<T>,
    ): List<T>? {
        if (!segment.match(node, transform)) {
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
