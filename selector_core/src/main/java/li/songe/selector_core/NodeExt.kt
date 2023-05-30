package li.songe.selector_core

interface NodeExt {
    val parent: NodeExt?
    val children: Sequence<NodeExt?>
    val name: CharSequence
    fun attr(name: String): Any?

    /**
     * constant traversal
     */
    fun getChild(offset: Int) = children.elementAtOrNull(offset)

    val ancestors: Sequence<NodeExt>
        get() = sequence {
            var parentVar: NodeExt? = parent ?: return@sequence
            while (parentVar != null) {
                yield(parentVar)
                parentVar = parentVar.parent
            }
        }

    fun getAncestor(offset: Int) = ancestors.elementAtOrNull(offset)

    //    if index=3, traverse 2,1,0
    val beforeBrothers: Sequence<NodeExt?>
        get() = sequence {
            val parentVal = parent ?: return@sequence
            val list = parentVal.children.takeWhile { it != this@NodeExt }.toMutableList()
            list.reverse()
            yieldAll(list)
        }

    fun getBeforeBrother(offset: Int) = beforeBrothers.elementAtOrNull(offset)

    //    if index=3, traverse 4,5,6...
    val afterBrothers: Sequence<NodeExt?>
        get() = sequence {
            val parentVal = parent ?: return@sequence
            yieldAll(parentVal.children.dropWhile { it != this@NodeExt }.drop(1))
        }

    fun getAfterBrother(offset: Int) = afterBrothers.elementAtOrNull(offset)

    val descendants: Sequence<NodeExt>
        get() = sequence {
//            深度优先先序遍历
//            https://developer.mozilla.org/zh-CN/docs/Web/API/Document/querySelector
            val stack = mutableListOf(this@NodeExt)
            val reverseList = mutableListOf<NodeExt>()
            do {
                val top = stack.removeLast()
                yield(top)
                for (childNode in top.children) {
                    if (childNode != null) {
                        reverseList.add(childNode)
                    }
                }
                if (reverseList.isNotEmpty()) {
                    reverseList.reverse()
                    stack.addAll(reverseList)
                    reverseList.clear()
                }
            } while (stack.isNotEmpty())
        }

    fun querySelector(selector: Selector) = querySelectorAll(selector).firstOrNull()

    fun querySelectorAll(selector: Selector) = sequence {
        descendants.forEach { node ->
            val r = selector.match(node)
            if (r != null)
                yield(r)
        }
    }
}



