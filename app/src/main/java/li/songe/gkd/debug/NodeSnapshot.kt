package li.songe.gkd.debug

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.gkd.selector.forEachIndexed
import java.util.ArrayDeque


/**
 * api/node 返回列表
 */

@Serializable
data class NodeSnapshot(
    val id: Int,
    val pid: Int,
    val index: Int,
    /**
     * null: when getChild(i) return null
     */
    val attr: AttrSnapshot?
) {
    companion object {
        fun abNodeToNode(
            nodeInfo: AccessibilityNodeInfo?,
            id: Int = 0,
            pid: Int = -1,
            index: Int = 0,
        ): NodeSnapshot {
            return NodeSnapshot(
                id,
                pid,
                index,
                nodeInfo?.let { AttrSnapshot.info2data(nodeInfo) }
            )
        }

        fun info2nodeList(nodeInfo: AccessibilityNodeInfo?): List<NodeSnapshot> {
            if (nodeInfo == null) {
                return emptyList()
            }
            val stack = ArrayDeque<Pair<Int, AccessibilityNodeInfo?>>()
            stack.push(0 to nodeInfo)
            val list = mutableListOf<NodeSnapshot>()
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
