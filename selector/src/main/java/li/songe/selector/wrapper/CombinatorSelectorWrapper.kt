package li.songe.selector.wrapper

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.forEachAncestorIndexed
import li.songe.selector.forEachElderBrotherIndexed
import li.songe.selector.forEachIndexed
import li.songe.selector.forEachYoungerBrotherIndexed
import li.songe.selector.getAncestor
import li.songe.selector.getBrother
import li.songe.selector.getDepth
import li.songe.selector.getIndex
import li.songe.selector.selector.CombinatorSelector

data class CombinatorSelectorWrapper(
    private val combinatorSelector: CombinatorSelector,
    val to: PropertySelectorWrapper,
) {
    override fun toString(): String {
        return to.toString() + "\u0020" + combinatorSelector.toString()
    }

    fun match(
        nodeInfo: AccessibilityNodeInfo,
        trackNodes: MutableList<AccessibilityNodeInfo?>,
    ): List<AccessibilityNodeInfo?>? {
        val expression = combinatorSelector.polynomialExpression
        val isConstant = expression.isConstant
        when (combinatorSelector.operator) {
            CombinatorSelector.Operator.Ancestor -> {
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (constantValue > 0) {
                        val ancestorNode = nodeInfo.getAncestor(constantValue)
                        if (ancestorNode != null) {
                            to.match(ancestorNode, trackNodes)?.let { return it }
                        }
                    }
                } else {
                    val maxDepth = nodeInfo.getDepth()
                    if (maxDepth <= 0) {
                        return null
                    }
                    val set = mutableSetOf<Int>()
                    repeat(maxDepth) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.forEachAncestorIndexed { depth, ancestorNode ->
                        if (set.contains(depth)) {
                            set.remove(depth)
                            to.match(ancestorNode, trackNodes)?.let { return it }
                        }
                    }
                }
            }
            CombinatorSelector.Operator.Child -> {
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (0 < constantValue && constantValue <= nodeInfo.childCount) {
                        val childNode: AccessibilityNodeInfo? = nodeInfo.getChild(constantValue - 1)
                        if (childNode != null) {
                            return to.match(childNode, trackNodes)
                        }
                    }
                } else {
                    if (nodeInfo.childCount <= 0) {
                        return null
                    }
                    val set = mutableSetOf<Int>()
                    repeat(nodeInfo.childCount) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.forEachIndexed { index, childNode ->
                        if (set.contains(index)) {
                            set.remove(index)
                            to.match(childNode, trackNodes)?.let { return it }
                        }
                    }
                }
            }
            CombinatorSelector.Operator.ElderBrother -> {
                val i = nodeInfo.getIndex() ?: return null
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (constantValue in 1..i) {
                        val brotherNode = nodeInfo.getBrother(constantValue)
                        if (brotherNode != null) {
                             to.match(brotherNode, trackNodes)?.let { return it }
                        }
                    }
                } else {
                    if (i <= 0) {
                        return null
                    }
                    val set = mutableSetOf<Int>()
                    repeat(i) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.forEachElderBrotherIndexed { offset, brotherNode ->
                        if (set.contains(offset)) {
                            set.remove(offset)
                            to.match(brotherNode, trackNodes)?.let { return it }
                        }
                    }
                }
            }
            CombinatorSelector.Operator.YoungerBrother -> {
                val i = nodeInfo.getIndex() ?: return null
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (0 < constantValue && i + constantValue < nodeInfo.parent.childCount) {
                        val brotherNode = nodeInfo.getBrother(constantValue, false)
                        if (brotherNode != null) {
                            to.match(brotherNode, trackNodes)?.let { return it }
                        }
                    }
                } else {
                    val parentNodeInfo = nodeInfo.parent ?: return null
                    if (parentNodeInfo.childCount - i - 1 <= 0) {
                        return null
                    }
                    val set = mutableSetOf<Int>()
                    repeat(parentNodeInfo.childCount - i - 1) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.forEachYoungerBrotherIndexed { offset, brotherNode ->
                        if (set.contains(offset)) {
                            set.remove(offset)
                            to.match(brotherNode, trackNodes)?.let { return it }
                        }
                    }
                }
            }
        }
        return null
    }
}
