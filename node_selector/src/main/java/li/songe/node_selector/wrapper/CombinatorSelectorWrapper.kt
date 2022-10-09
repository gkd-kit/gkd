package li.songe.node_selector.wrapper

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.node_selector.*
import li.songe.node_selector.selector.CombinatorSelector

data class CombinatorSelectorWrapper(
    private val combinatorSelector: CombinatorSelector,
    val to: PropertySelectorWrapper
) {
    override fun toString(): String {
        return to.toString() + "\u0020" + combinatorSelector.toString()
    }

    fun match(nodeInfo: AccessibilityNodeInfo): Boolean {
        val expression = combinatorSelector.polynomialExpression
        val isConstant = expression.isConstant
        when (combinatorSelector.operator) {
            CombinatorSelector.Operator.Ancestor -> {
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (constantValue > 0) {
                        nodeInfo.traverseAncestor { depth, ancestorNode ->
                            if (depth == constantValue) {
                                val targetNode = to.match(ancestorNode)
                                if (targetNode) {
                                    return targetNode
                                }
                                return@traverseAncestor true
                            }
                            return@traverseAncestor Unit
                        }
                    }
                } else {
                    val maxDepth = nodeInfo.getDepth()
                    if (maxDepth <= 0) {
                        return false
                    }
                    val set = mutableSetOf<Int>()
                    repeat(maxDepth) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.traverseAncestor { depth, ancestorNode ->
                        if (set.contains(depth)) {
                            set.remove(depth)
                            val targetNode = to.match(ancestorNode)
                            if (targetNode) {
                                return targetNode
                            }
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
                            return to.match(childNode)
                        }
                    }
                } else {
                    if (nodeInfo.childCount <= 0) {
                        return false
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
                            val targetNode = to.match(childNode)
                            if (targetNode) {
                                return targetNode
                            }
                        }
                    }
                }
            }
            CombinatorSelector.Operator.ElderBrother -> {
                val i = nodeInfo.getIndex() ?: return false
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (constantValue in 1..i) {
                        nodeInfo.traverseElderBrother { offset, brotherNode ->
                            if (offset == constantValue) {
                                val targetNode = to.match(brotherNode)
                                if (targetNode) {
                                    return targetNode
                                }
                                return@traverseElderBrother true
                            }
                            return@traverseElderBrother Unit
                        }
                    }
                } else {
                    if (i <= 0) {
                        return false
                    }
                    val set = mutableSetOf<Int>()
                    repeat(i) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.traverseElderBrother { offset, brotherNode ->
                        if (set.contains(offset)) {
                            set.remove(offset)
                            val targetNode = to.match(brotherNode)
                            if (targetNode) {
                                return targetNode
                            }
                        }
                    }
                }
            }
            CombinatorSelector.Operator.YoungerBrother -> {
                val i = nodeInfo.getIndex() ?: return false
                if (isConstant) {
                    val constantValue = expression.calculate()
                    if (0 < constantValue && i + constantValue < nodeInfo.parent.childCount) {
                        nodeInfo.traverseYoungerBrother { offset, brotherNode ->
                            if (offset == constantValue) {
                                val targetNode = to.match(brotherNode)
                                if (targetNode) {
                                    return targetNode
                                }
                                return@traverseYoungerBrother true
                            }
                            return@traverseYoungerBrother Unit
                        }
                    }
                } else {
                    val parentNodeInfo = nodeInfo.parent ?: return false
                    if (parentNodeInfo.childCount - i - 1 <= 0) {
                        return false
                    }
                    val set = mutableSetOf<Int>()
                    repeat(parentNodeInfo.childCount - i - 1) {
                        val v = expression.calculate(it + 1)
                        if (v > 0) {
                            set.add(v)
                        }
                    }
                    nodeInfo.traverseYoungerBrother { offset, brotherNode ->
                        if (set.contains(offset)) {
                            set.remove(offset)
                            val targetNode = to.match(brotherNode)
                            if (targetNode) {
                                return targetNode
                            }
                        }
                    }
                }
            }
        }
        return false
    }
}
