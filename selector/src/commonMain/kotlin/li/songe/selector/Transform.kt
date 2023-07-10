package li.songe.selector


class Transform<T>(
    val getAttr: (T, String) -> Any?,
    val getName: (T) -> CharSequence?,
    val getChildren: (T) -> Sequence<T?>,
    val getChild: (T, Int) -> T? = { node, offset -> getChildren(node).elementAtOrNull(offset) },
    val getParent: (T) -> T?,
    val getAncestors: (T) -> Sequence<T> = { node ->
        sequence {
            var parentVar: T? = getParent(node) ?: return@sequence
            while (parentVar != null) {
                parentVar?.let {
                    yield(it)
                    parentVar = getParent(it)
                }
            }
        }
    },
    val getAncestor: (T, Int) -> T? = { node, offset -> getAncestors(node).elementAtOrNull(offset) },

    val getBeforeBrothers: (T) -> Sequence<T?> = { node ->
        sequence {
            val parentVal = getParent(node) ?: return@sequence
            val list = getChildren(parentVal).takeWhile { it != node }.toMutableList()
            list.reverse()
            yieldAll(list)
        }
    },
    val getBeforeBrother: (T, Int) -> T? = { node, offset ->
        getBeforeBrothers(node).elementAtOrNull(
            offset
        )
    },

    val getAfterBrothers: (T) -> Sequence<T?> = { node ->
        sequence {
            val parentVal = getParent(node) ?: return@sequence
            yieldAll(getChildren(parentVal).dropWhile { it != node }.drop(1))
        }
    },
    val getAfterBrother: (T, Int) -> T? = { node, offset ->
        getAfterBrothers(node).elementAtOrNull(
            offset
        )
    },

    val getDescendants: (T) -> Sequence<T> = { node ->
        sequence { //            深度优先 先序遍历
            //            https://developer.mozilla.org/zh-CN/docs/Web/API/Document/querySelector
            val stack = mutableListOf(node)
            val tempNodes = mutableListOf<T>()
            do {
                val top = stack.removeLast()
                yield(top)
                for (childNode in getChildren(top)) {
                    if (childNode != null) {
                        tempNodes.add(childNode)
                    }
                }
                if (tempNodes.isNotEmpty()) {
                    for (i in tempNodes.size - 1 downTo 0) {
                        stack.add(tempNodes[i])
                    }
                    tempNodes.clear()
                }
            } while (stack.isNotEmpty())
        }
    },

    ) {
    val querySelectorAll: (T, Selector) -> Sequence<T> = { node, selector ->
        sequence {
            val trackNodes: MutableList<T> = mutableListOf()
            getDescendants(node).forEach { childNode ->
                val r = selector.match(childNode, this@Transform, trackNodes)
                trackNodes.clear()
                if (r != null) yield(r)
            }
        }
    }
    val querySelector: (T, Selector) -> T? = { node, selector ->
        querySelectorAll(
            node, selector
        ).firstOrNull()
    }
    val querySelectorTrackAll: (T, Selector) -> Sequence<List<T>> = { node, selector ->
        sequence {
            val trackNodes: MutableList<T> = mutableListOf()
            getDescendants(node).forEach { childNode ->
                val r = selector.matchTracks(childNode, this@Transform, trackNodes)?.toList()
                trackNodes.clear()
                if (r != null) yield(r)
            }
        }
    }
    val querySelectorTrack: (T, Selector) -> List<T>? = { node, selector ->
        querySelectorTrackAll(
            node, selector
        ).firstOrNull()
    }
}