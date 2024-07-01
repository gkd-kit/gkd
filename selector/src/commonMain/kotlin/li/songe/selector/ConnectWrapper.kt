package li.songe.selector

data class ConnectWrapper(
    val segment: ConnectSegment,
    val to: PropertyWrapper,
) {
    override fun toString(): String {
        return (to.toString() + "\u0020" + segment.toString()).trim()
    }

    internal fun <T> matchTracks(
        node: T, transform: Transform<T>,
        trackNodes: MutableList<T>,
    ): List<T>? {
        segment.traversal(node, transform).forEach {
            if (it == null) return@forEach
            val r = to.matchTracks(it, transform, trackNodes)
            if (r != null) return r
        }
        return null
    }
}