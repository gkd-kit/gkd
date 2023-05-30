package li.songe.selector_core.data

import li.songe.selector_core.NodeExt

data class ConnectWrapper(
    val connectSegment: ConnectSegment,
    val to: PropertyWrapper,
) {
    override fun toString(): String {
        return (to.toString() + "\u0020" + connectSegment.toString()).trim()
    }

    fun match(
        node: NodeExt,
        trackNodes: MutableList<NodeExt> = mutableListOf(),
    ): List<NodeExt>? {
        connectSegment.traversal(node).forEach {
            if (it == null) return@forEach
            val r = to.match(it, trackNodes)
            if (r != null) return r
        }
        return null
    }
}