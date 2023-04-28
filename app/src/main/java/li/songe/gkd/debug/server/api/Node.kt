package li.songe.gkd.debug.server.api

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.selector.forEach
import java.util.ArrayDeque


/**
 * api/node 返回列表
 */

@Serializable
data class Node(
    val id: Int,
    val pid: Int,
    val attr: Attr
) {
    companion object {
        fun info2data(
            nodeInfo: AccessibilityNodeInfo,
            id: Int = 0,
            pid: Int = -1
        ): Node {
            return Node(
                id,
                pid,
                Attr.info2data(nodeInfo)
            )
        }

        fun info2nodeList(nodeInfo: AccessibilityNodeInfo?): List<Node> {
            if (nodeInfo == null) {
                return emptyList()
            }
            val stack = ArrayDeque<Pair<Int, AccessibilityNodeInfo>>()
            stack.push(0 to nodeInfo)
            val list = mutableListOf<Node>()
            list.add(info2data(nodeInfo))
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                top.second.forEach {  childNode ->
                    stack.push(list.size to childNode)
                    list.add(info2data(childNode, list.size, top.first))
                }
            }
            return list
        }
    }
}
