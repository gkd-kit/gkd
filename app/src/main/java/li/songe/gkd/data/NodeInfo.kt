package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.gkd.accessibility.forEachIndexed
import java.util.ArrayDeque

@Serializable
data class NodeInfo(
    val id: Int, val pid: Int, val index: Int,
    /**
     * null: when getChild(i) return null
     */
    val attr: AttrInfo?
) {
    companion object {
        fun abNodeToNode(
            nodeInfo: AccessibilityNodeInfo?,
            id: Int = 0,
            pid: Int = -1,
            index: Int = 0,
        ): NodeInfo {
            return NodeInfo(id, pid, index, nodeInfo?.let { AttrInfo.info2data(nodeInfo) })
        }

        fun info2nodeList(nodeInfo: AccessibilityNodeInfo?): List<NodeInfo> {
            if (nodeInfo == null) {
                return emptyList()
            }
            val stack = ArrayDeque<Pair<Int, AccessibilityNodeInfo?>>()
            stack.push(0 to nodeInfo)
            val list = mutableListOf<NodeInfo>()
            list.add(abNodeToNode(nodeInfo, index = 0))
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                top.second?.forEachIndexed { index, childNode ->
                    stack.push(list.size to childNode)
                    list.add(abNodeToNode(childNode, list.size, top.first, index))
                }
            }
            return list
        }
    }
}
