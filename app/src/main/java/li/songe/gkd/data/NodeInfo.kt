package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.service.forEachIndexed
import li.songe.gkd.service.topActivityFlow
import java.util.ArrayDeque

@Serializable
data class NodeInfo(
    val id: Int, val pid: Int, val index: Int,
    /**
     * null: when getChild(i) return null
     */
    val attr: AttrInfo?,
) {
    companion object {
        fun abNodeToNode(
            nodeInfo: AccessibilityNodeInfo?,
            id: Int = 0,
            pid: Int = -1,
            index: Int = 0,
            depth: Int = 0,
        ): NodeInfo {
            return NodeInfo(
                id,
                pid,
                index,
                nodeInfo?.let { AttrInfo.info2data(nodeInfo, index, depth) })
        }

        private const val MAX_KEEP_SIZE = 5000

        fun info2nodeList(nodeInfo: AccessibilityNodeInfo?): List<NodeInfo> {
            if (nodeInfo == null) {
                return emptyList()
            }
            /**
             * [node, id, depth]
             */
            val stack = ArrayDeque<Tuple3<AccessibilityNodeInfo?, Int, Int>>()
            stack.push(Tuple3(nodeInfo, 0, 0))
            val list = mutableListOf<NodeInfo>()
            list.add(abNodeToNode(nodeInfo, index = 0))
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                top.t0?.forEachIndexed { index, childNode ->
                    stack.push(Tuple3(childNode, list.size, top.t2 + 1))
                    list.add(abNodeToNode(childNode, list.size, top.t1, index, top.t2 + 1))
                }
                if (list.size > MAX_KEEP_SIZE) {
//                    https://github.com/gkd-kit/gkd/issues/28
                    ToastUtils.showShort("节点数量至多保留$MAX_KEEP_SIZE,丢弃后续节点")
                    LogUtils.w(
                        nodeInfo.packageName, topActivityFlow.value?.activityId, "节点数量过多"
                    )
                    break
                }
            }
            return list
        }
    }
}
