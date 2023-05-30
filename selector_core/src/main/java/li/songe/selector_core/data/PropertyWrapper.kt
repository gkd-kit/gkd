package li.songe.selector_core.data

import li.songe.selector_core.NodeExt

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

    fun match(
        node: NodeExt,
        trackNodes: MutableList<NodeExt> = mutableListOf(),
    ): List<NodeExt>? {
        if (!propertySegment.match(node)) {
            return null
        }
        if (propertySegment.tracked || trackNodes.isEmpty()) {
            trackNodes.add(node)
        }
        if (to == null) {
            return trackNodes
        }
        return to.match(node, trackNodes)
    }
}
