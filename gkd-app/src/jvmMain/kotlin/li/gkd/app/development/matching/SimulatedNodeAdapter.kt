package li.gkd.app.development.matching

import java.util.IdentityHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import li.gkd.app.matching.diagnostics.MatchSimulatedNode
import li.gkd.selector.NodeAdapter

/** Direct access to the same immutable nodes used by simulation and benchmarking. */
class SimulatedNodeAdapter(root: MatchSimulatedNode, private val queryJob: Job) :
    NodeAdapter<MatchSimulatedNode>() {
    private val parents = IdentityHashMap<MatchSimulatedNode, MatchSimulatedNode>()
    private val keys = IdentityHashMap<MatchSimulatedNode, Int>()

    init {
        val pending = ArrayDeque<MatchSimulatedNode>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            queryJob.ensureActive()
            val node = pending.removeFirst()
            require(keys.size < 20000) { "Simulated node limit exceeded" }
            keys[node] = keys.size
            node.children.forEach {
                parents[it] = node
                pending.addLast(it)
            }
        }
    }

    override fun getAttr(target: Any, name: String): Any? {
        queryJob.ensureActive()
        val node = target as? MatchSimulatedNode ?: return null
        return when (name) {
            "name" -> node.name
            "childCount" -> node.children.size
            "index" -> parents[node]?.children?.indexOfFirst { it === node } ?: 0
            "parent" -> parents[node]
            "depth" -> {
                var depth = 0
                var parent = parents[node]
                while (parent != null) {
                    queryJob.ensureActive()
                    depth++
                    parent = parents[parent]
                }
                depth
            }
            else ->
                node.attributes[name]?.let {
                    if (it.isString) it.content
                    else it.booleanOrNull ?: it.intOrNull ?: it.doubleOrNull
                }
        }
    }

    override fun getName(node: MatchSimulatedNode) = node.name.also { queryJob.ensureActive() }

    override fun getChildCount(node: MatchSimulatedNode) =
        node.children.size.also { queryJob.ensureActive() }

    override fun getChild(node: MatchSimulatedNode, index: Int) =
        node.children.getOrNull(index).also { queryJob.ensureActive() }

    override fun getParent(node: MatchSimulatedNode) = parents[node]

    override fun getNodeKey(node: MatchSimulatedNode): Any = keys.getValue(node)
}
