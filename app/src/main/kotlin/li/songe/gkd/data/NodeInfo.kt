package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.service.getChildren
import li.songe.gkd.service.topActivityFlow
import li.songe.gkd.util.toast
import kotlin.system.measureTimeMillis

@Serializable
data class NodeInfo(
    val id: Int,
    val pid: Int,
    val idQf: Boolean?,
    val textQf: Boolean?,
    val attr: AttrInfo,
) {

    companion object {

        private const val MAX_KEEP_SIZE = 5000

        // 先获取所有节点构建树结构, 然后再判断 idQf/textQf 如果存在一个能同时 idQf 和 textQf 的节点, 则认为 idQf 和 textQf 等价

        data class TempNodeData(
            val node: AccessibilityNodeInfo,
            val parent: TempNodeData?,
            val index: Int,
            val depth: Int,
        ) {
            var id = 0
            val attr = AttrInfo.info2data(node, index, depth)
            var children: List<TempNodeData> = emptyList()

            var idQfInit = false
            var idQf: Boolean? = null
                set(value) {
                    field = value
                    idQfInit = true
                }
            var textQfInit = false
            var textQf: Boolean? = null
                set(value) {
                    field = value
                    textQfInit = true
                }
        }

        fun info2nodeList(root: AccessibilityNodeInfo?): List<NodeInfo> {
            if (root == null) {
                return emptyList()
            }
            val nodes = mutableListOf<TempNodeData>()
            val collectTime = measureTimeMillis {
                val stack = mutableListOf<TempNodeData>()
                var times = 0
                stack.add(TempNodeData(root, null, 0, 0))
                while (stack.isNotEmpty()) {
                    times++
                    val node = stack.removeLast()
                    node.id = times - 1
                    val children = getChildren(node.node).mapIndexed { i, child ->
                        TempNodeData(
                            child, node, i, node.depth + 1
                        )
                    }.toList()
                    node.children = children
                    nodes.add(node)
                    repeat(children.size) { i ->
                        stack.add(children[children.size - i - 1])
                    }
                    if (times > MAX_KEEP_SIZE) {
                        // https://github.com/gkd-kit/gkd/issues/28
                        toast("节点数量至多保留$MAX_KEEP_SIZE,丢弃后续节点")
                        LogUtils.w(
                            root.packageName, topActivityFlow.value.activityId, "节点数量过多"
                        )
                        break
                    }
                }
            }
            val qfTime = measureTimeMillis {
                val idQfCache = mutableMapOf<String, List<AccessibilityNodeInfo>>()
                val textQfCache = mutableMapOf<String, List<AccessibilityNodeInfo>>()
                var idTextQf = false
                fun updateQf(n: TempNodeData) {
                    if (!n.idQfInit && !n.attr.id.isNullOrEmpty()) {
                        n.idQf = (idQfCache[n.attr.id]
                            ?: root.findAccessibilityNodeInfosByViewId(n.attr.id)).apply {
                            idQfCache[n.attr.id] = this
                        }
                            .any { t -> t == n.node }

                    }

                    if (!n.textQfInit && !n.attr.text.isNullOrEmpty()) {
                        n.textQf = (textQfCache[n.attr.text]
                            ?: root.findAccessibilityNodeInfosByText(n.attr.text)).apply {
                            textQfCache[n.attr.text] = this
                        }
                            .any { t -> t == n.node }
                    }

                    if (n.idQf == true && n.textQf == true) {
                        idTextQf = true
                    }

                    if (!n.idQfInit && n.idQf != null) {
                        n.parent?.children?.forEach { c ->
                            c.idQf = n.idQf
                            if (idTextQf) {
                                c.textQf = n.textQf
                            }
                        }
                        if (n.idQf == true) {
                            var p = n.parent
                            while (p != null && !p.idQfInit) {
                                p.idQf = n.idQf
                                if (idTextQf) {
                                    p.textQf = n.textQf
                                }
                                p = p.parent
                                p?.children?.forEach { bro ->
                                    bro.idQf = n.idQf
                                    if (idTextQf) {
                                        bro.textQf = n.textQf
                                    }
                                }
                            }
                        } else {
                            val tempStack = mutableListOf(n)
                            while (tempStack.isNotEmpty()) {
                                val top = tempStack.removeLast()
                                top.idQf = n.idQf
                                if (idTextQf) {
                                    top.textQf = n.textQf
                                }
                                repeat(top.children.size) { i ->
                                    tempStack.add(top.children[top.children.size - i - 1])
                                }
                            }
                        }
                    }

                    if (!n.textQfInit && n.textQf != null) {
                        n.parent?.children?.forEach { c ->
                            c.textQf = n.textQf
                            if (idTextQf) {
                                c.idQf = n.idQf
                            }
                        }
                        if (n.textQf == true) {
                            var p = n.parent
                            while (p != null && !p.textQfInit) {
                                p.textQf = n.textQf
                                if (idTextQf) {
                                    p.idQf = n.idQf
                                }
                                p = p.parent
                                p?.children?.forEach { bro ->
                                    bro.textQf = n.textQf
                                    if (idTextQf) {
                                        bro.idQf = bro.idQf
                                    }
                                }
                            }
                        } else {
                            val tempStack = mutableListOf(n)
                            while (tempStack.isNotEmpty()) {
                                val top = tempStack.removeLast()
                                top.textQf = n.textQf
                                if (idTextQf) {
                                    top.idQf = n.idQf
                                }
                                repeat(top.children.size) { i ->
                                    tempStack.add(top.children[top.children.size - i - 1])
                                }
                            }
                        }
                    }

                    n.idQfInit = true
                    n.textQfInit = true
                }
                for (i in (nodes.size - 1) downTo 0) {
                    val n = nodes[i]
                    if (n.children.isEmpty()) {
                        updateQf(n)
                    }
                }
                for (i in (nodes.size - 1) downTo 0) {
                    val n = nodes[i]
                    if (n.children.isNotEmpty()) {
                        updateQf(n)
                    }
                }
            }

            LogUtils.d(
                topActivityFlow.value,
                "快照节点数量:${nodes.size}, 总耗时:${collectTime + qfTime}ms",
                "收集节点耗时:${collectTime}ms, 收集quickFind耗时:${qfTime}ms",
            )

            return nodes.map { n ->
                NodeInfo(
                    id = n.id,
                    pid = n.parent?.id ?: -1,
                    idQf = n.idQf,
                    textQf = n.textQf,
                    attr = n.attr
                )
            }
        }
    }
}
