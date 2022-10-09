package li.songe.gkd.data.api

import android.view.accessibility.AccessibilityNodeInfo
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import li.songe.node_selector.forEachIndexed
import java.util.*
import kotlinx.serialization.Serializable


/**
 * api/node 返回列表
 */

@Serializable
@JsonClass(generateAdapter = true)
data class NodeData(
    @Json(name = "id")
    val id: Int,
    @Json(name = "pid")
    val pid: Int,
    @Json(name = "attr")
    val attrData: AttrData
) {
    companion object {
        fun info2data(
            nodeInfo: AccessibilityNodeInfo,
            id: Int = 0,
            pid: Int = -1
        ): NodeData {
            return NodeData(
                id,
                pid,
                AttrData.info2data(nodeInfo)
            )
        }

        fun info2nodeList(nodeInfo: AccessibilityNodeInfo?): List<NodeData> {
            if (nodeInfo == null) {
                return emptyList()
            }
            val stack = Stack<Pair<Int, AccessibilityNodeInfo>>()
            stack.push(0 to nodeInfo)
            val list = mutableListOf<NodeData>()
            list.add(info2data(nodeInfo))
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                top.second.forEachIndexed { _, childNode ->
                    stack.push(list.size to childNode)
                    list.add(info2data(childNode, list.size, top.first))
                    return@forEachIndexed Unit
                }
            }
            return list
        }
    }
}
