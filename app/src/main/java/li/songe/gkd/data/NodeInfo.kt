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
    val id: Int,
    val pid: Int,
    val index: Int,
    /**
     * - null: 不满足查找条件
     * - true: 可查找的
     * - false: 不可查找的
     */
    val quickFind: Boolean?,
    /**
     * null: when getChild(i) return null
     */
    val attr: AttrInfo?,
) {
    companion object {

        private fun getQuickFind(
            rootNodeInfo: AccessibilityNodeInfo,
            nodeInfo: AccessibilityNodeInfo?,
            quickFind: Boolean?,
        ): Boolean? {
            // 如果父节点是无法查找的, 则所有子节点都是无法查找的
            // 如果一个节点是可查找的, 则所有兄弟节点都是可查找的(如果满足条件)
            return if (nodeInfo != null) {
                val viewId = nodeInfo.viewIdResourceName
                if (viewId != null && viewId.isNotEmpty()) { // id='' 不满足查找
                    quickFind ?: rootNodeInfo.findAccessibilityNodeInfosByViewId(viewId)
                        .any { n -> n == nodeInfo }
                } else {
                    val viewText = nodeInfo.text
                    if (viewText != null && viewText.isNotEmpty()) { // text='' 不满足查找
                        quickFind
                            ?: rootNodeInfo.findAccessibilityNodeInfosByText(viewText.toString())
                                .any { n -> n == nodeInfo }
                    } else {
                        null
                    }
                }
            } else {
                null
            }
        }

        fun abNodeToNode(
            rootNodeInfo: AccessibilityNodeInfo,
            nodeInfo: AccessibilityNodeInfo?,
            id: Int = 0,
            pid: Int = -1,
            index: Int = 0,
            depth: Int = 0,
            parent: NodeInfo? = null,
            brother: NodeInfo? = null,
        ): NodeInfo {
            // 如果父节点是不可查找的, 则下面所有子节点都是不可查找的
            // 兄弟节点的可查找性一致
            return NodeInfo(id = id, pid = pid, index = index, quickFind = getQuickFind(
                rootNodeInfo, nodeInfo, if (parent?.quickFind == false) {
                    false
                } else {
                    brother?.quickFind
                }
            ), attr = nodeInfo?.let { AttrInfo.info2data(nodeInfo, index, depth) })
        }

        private const val MAX_KEEP_SIZE = 5000

        fun info2nodeList(rootNodeInfo: AccessibilityNodeInfo?): List<NodeInfo> {
            if (rootNodeInfo == null) {
                return emptyList()
            }
            val st = System.currentTimeMillis()

            /**
             * [node, id, depth]
             */
            val stack = ArrayDeque<Tuple3<AccessibilityNodeInfo?, Int, Int>>()
            stack.push(Tuple3(rootNodeInfo, 0, 0))
            val list = mutableListOf<NodeInfo>()
            list.add(abNodeToNode(rootNodeInfo, rootNodeInfo, index = 0))
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                val pid = top.t1
                val childDepth = top.t2 + 1
                var brother: NodeInfo? = null
                top.t0?.forEachIndexed { index, childNode ->
                    val id = list.size
                    stack.push(Tuple3(childNode, id, childDepth))
                    val simpleNode = abNodeToNode(
                        rootNodeInfo, childNode, id, pid, index, childDepth, list[pid], brother
                    )
                    list.add(simpleNode)
                    brother = if (simpleNode.quickFind != null) {
                        simpleNode
                    } else {
                        brother
                    }
                }
                brother = null
                if (list.size > MAX_KEEP_SIZE) {
//                    https://github.com/gkd-kit/gkd/issues/28
                    ToastUtils.showShort("节点数量至多保留$MAX_KEEP_SIZE,丢弃后续节点")
                    LogUtils.w(
                        rootNodeInfo.packageName, topActivityFlow.value?.activityId, "节点数量过多"
                    )
                    break
                }
            }
            LogUtils.d(
                topActivityFlow.value,
                "快照收集:${list.size}, 耗时:${System.currentTimeMillis() - st}ms"
            )
            return list
        }
    }
}
