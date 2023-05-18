package li.songe.selector_core

interface Node {
    val parent: Node?
    val children: Sequence<Node?>

    val name: CharSequence

    /**
     * constant traversal
     */
    fun getChild(offset: Int) = children.elementAtOrNull(offset)

    fun attr(name: String): Any?

    val ancestors: Sequence<Node>
        get() = sequence {
            var parentVar: Node? = parent ?: return@sequence
            while (parentVar != null) {
                yield(parentVar)
                parentVar = parentVar.parent
            }
        }

    fun getAncestor(offset: Int) = ancestors.elementAtOrNull(offset)

    //    if index=3, traverse 2,1,0
    val beforeBrothers: Sequence<Node?>
        get() = sequence {
            val parentVal = parent ?: return@sequence
            val list = parentVal.children.takeWhile { it != this@Node }.toMutableList()
            list.reverse()
            yieldAll(list)
        }

    fun getBeforeBrother(offset: Int) = beforeBrothers.elementAtOrNull(offset)

    //    if index=3, traverse 4,5,6...
    val afterBrothers: Sequence<Node?>
        get() = sequence {
            val parentVal = parent ?: return@sequence
            yieldAll(parentVal.children.dropWhile { it == this@Node })
        }

    fun getAfterBrother(offset: Int) = afterBrothers.elementAtOrNull(offset)

    val descendants: Sequence<Node>
        get() = sequence {
            val stack = mutableListOf<Node>()
            stack.add(this@Node)
            do {
                val top = stack.removeLast()
                yield(top)
                for (childNode in top.children) {
                    if (childNode != null) {
                        stack.add(childNode)
                    }
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


