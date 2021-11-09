package li.songe.ad_closer.util

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils

/**
 * @param pathIndexList 当前节点在节点树的路径, 最后一项代表节点是父节点第几个元素, 第一项是 0
 */
private fun match(
    nodeInfo: AccessibilityNodeInfo,
    matchUnit: MatchUnit,
    pathIndexList: List<Int>
): Boolean {
//    val react = Rect()
//    nodeInfo.getBoundsInScreen(react)
//    val debug = nodeInfo.viewIdResourceName==null
//            && nodeInfo.className.endsWith("android.widget.Image")
//            && react.left==942
//            && matchUnit.className== "Image"
//    匹配最小节点深度
    var miniDepth = 1
    var r = matchUnit.relationUnit
    while (r != null) {
        if (r.operator !is RelationUnit.Operator.Brother) {
            miniDepth += 1
        }
        r = r.to.relationUnit
    }
    if (pathIndexList.size <= miniDepth) {
        return false
    }
//    匹配类名
    if (!nodeInfo.className.endsWith(matchUnit.className)) {
        return false
    }
    val childCount = nodeInfo.childCount
    val text: CharSequence? = nodeInfo.text
    val id: String? = nodeInfo.viewIdResourceName

//    在属性匹配列表不空的情况下, 列表所有项都匹配
    if (matchUnit.attributeSelectorList.isNotEmpty()) {
        val condition2 = matchUnit.attributeSelectorList.all {
            when (it.attr) {
                AttributeSelector.Attribute.ChildCount -> when (it.operator) {
                    AttributeSelector.Operator.End -> false
                    AttributeSelector.Operator.Equal -> childCount == it.value.toInt()
                    AttributeSelector.Operator.Include -> false
                    AttributeSelector.Operator.Less -> childCount < it.value.toInt()
                    AttributeSelector.Operator.More -> childCount > it.value.toInt()
                    AttributeSelector.Operator.Start -> false
                    else -> TODO()
                }
                AttributeSelector.Attribute.Id -> {
                    when (it.operator) {
                        AttributeSelector.Operator.End -> false
                        AttributeSelector.Operator.Equal -> id == it.value
                        AttributeSelector.Operator.Include -> false
                        AttributeSelector.Operator.Less -> false
                        AttributeSelector.Operator.More -> false
                        AttributeSelector.Operator.Start -> false
                        else -> TODO()
                    }
                }
                AttributeSelector.Attribute.Text -> text != null && when (it.operator) {
                    AttributeSelector.Operator.End -> text.endsWith(it.value)
                    AttributeSelector.Operator.Equal -> text == it.value
                    AttributeSelector.Operator.Include -> text.contains(it.value)
                    AttributeSelector.Operator.Less -> false
                    AttributeSelector.Operator.More -> false
                    AttributeSelector.Operator.Start -> text.startsWith(it.value)
                    else -> TODO()
                }
                else -> TODO()
            }
        }
        if (!condition2) {
            return false
        }
    }

    val relationUnit = matchUnit.relationUnit ?: return true
//    目前有 父亲/祖先/兄弟 关系 此节点必须有 父节点
    val parent: AccessibilityNodeInfo? = nodeInfo.parent
    if (parent != null) {
        when (relationUnit.operator) {
            RelationUnit.Operator.Ancestor -> {
                var p = parent
                var pl = pathIndexList.subList(0, pathIndexList.size - 1)
                while (p != null) {
                    if (match(p, relationUnit.to, pl)) {
                        return true
                    }
                    p = p.parent
                    pl = pl.subList(0, pl.size - 1)
                }
            }
            is RelationUnit.Operator.Brother -> {
                val brotherIndex = pathIndexList.last() - relationUnit.operator.offset
                return if (brotherIndex !in 0 until parent.childCount) {
                    false
                } else {
                    val brother = parent.getChild(brotherIndex)
                    if (brother == null) {
                        false
                    } else {
                        val pl = pathIndexList.toMutableList()
                        pl[pl.size - 1] = brotherIndex
                        match(brother, relationUnit.to, pl.toList())
                    }

                }
            }
            RelationUnit.Operator.Parent -> {
                return match(
                    parent,
                    relationUnit.to,
                    pathIndexList.subList(0, pathIndexList.size - 1)
                )
            }
        }
    }
    return false
}

fun findNodeInfo(
    nodeInfo: AccessibilityNodeInfo?,
    matchUnit: MatchUnit,
    pathIndexList: List<Int>,
): AccessibilityNodeInfo? {
    if (nodeInfo == null) {
        return null
    }
    if (match(nodeInfo, matchUnit, pathIndexList)) {
        return nodeInfo
    }
    var nodeInfo1: AccessibilityNodeInfo? = null
    run loop@{
        nodeInfo.forEachIndexed { index, child ->
            val nowPathList = pathIndexList.toMutableList().apply { add(index) }
            nodeInfo1 = findNodeInfo(child, matchUnit, nowPathList)
            if (nodeInfo1 != null) {
                return@loop
            }
        }
    }
    return nodeInfo1
}

//inline fun AccessibilityNodeInfo.forEach(action: (AccessibilityNodeInfo) -> Unit): Unit {
//    var index = 0
//    while (index < childCount) {
//        val child: AccessibilityNodeInfo? = getChild(index)
//        if (child != null) {
//            action(child)
//        }
//        index += 1
//    }
//}

inline fun AccessibilityNodeInfo.forEachIndexed(action: (index: Int, AccessibilityNodeInfo) -> Unit) {
    var index = 0
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        if (child != null) {
            action(index, child)
        }
        index += 1
    }
}