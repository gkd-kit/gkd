package li.songe.selector_core.data

import li.songe.selector_core.Node

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
        node: Node,
        trackNodes: MutableList<Node> = mutableListOf(),
    ): List<Node>? {
        if (!propertySegment.match(node)) {
            return null
        }
        if (propertySegment.match || trackNodes.isEmpty()) {
            trackNodes.add(node)
        }
        if (to == null) {
            return trackNodes
        }
        return to.match(node, trackNodes)
    }
}
