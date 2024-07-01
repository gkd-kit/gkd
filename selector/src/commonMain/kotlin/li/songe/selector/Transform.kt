package li.songe.selector

@Suppress("UNUSED")
class Transform<T>(
    val getAttr: (Any?, String) -> Any?,
    val getInvoke: (Any?, String, List<Any?>) -> Any? = { _, _, _ -> null },
    val getName: (T) -> CharSequence?,
    val getChildren: (T) -> Sequence<T>,
    val getParent: (T) -> T?,

    val getDescendants: (T) -> Sequence<T> = { node ->
        sequence { //            深度优先 先序遍历
            //            https://developer.mozilla.org/zh-CN/docs/Web/API/Document/querySelector
            val stack = getChildren(node).toMutableList()
            if (stack.isEmpty()) return@sequence
            stack.reverse()
            val tempNodes = mutableListOf<T>()
            do {
                val top = stack.removeLast()
                yield(top)
                for (childNode in getChildren(top)) {
                    // 可针对 sequence 优化
                    tempNodes.add(childNode)
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

    val getChildrenX: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        getChildren(node)
            .let {
                if (connectExpression.maxOffset != null) {
                    it.take(connectExpression.maxOffset!! + 1)
                } else {
                    it
                }
            }
            .filterIndexed { i, _ ->
                connectExpression.checkOffset(
                    i
                )
            }
    },
    val getAncestors: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        sequence {
            var parentVar: T? = getParent(node) ?: return@sequence
            var offset = 0
            while (parentVar != null) {
                parentVar?.let {
                    if (connectExpression.checkOffset(offset)) {
                        yield(it)
                    }
                    offset++
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) {
                            return@sequence
                        }
                    }
                    parentVar = getParent(it)
                }
            }
        }
    },
    val getBeforeBrothers: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        val parentVal = getParent(node)
        if (parentVal != null) {
            val list = getChildren(parentVal).takeWhile { it != node }.toMutableList()
            list.reverse()
            list.asSequence().filterIndexed { i, _ ->
                connectExpression.checkOffset(
                    i
                )
            }
        } else {
            emptySequence()
        }
    },
    val getAfterBrothers: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        val parentVal = getParent(node)
        if (parentVal != null) {
            getChildren(parentVal).dropWhile { it != node }
                .drop(1)
                .let {
                    if (connectExpression.maxOffset != null) {
                        it.take(connectExpression.maxOffset!! + 1)
                    } else {
                        it
                    }
                }.filterIndexed { i, _ ->
                    connectExpression.checkOffset(
                        i
                    )
                }
        } else {
            emptySequence()
        }
    },

    val getDescendantsX: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        sequence {
            val stack = getChildren(node).toMutableList()
            if (stack.isEmpty()) return@sequence
            stack.reverse()
            val tempNodes = mutableListOf<T>()
            var offset = 0
            do {
                val top = stack.removeLast()
                if (connectExpression.checkOffset(offset)) {
                    yield(top)
                }
                offset++
                connectExpression.maxOffset?.let { maxOffset ->
                    if (offset > maxOffset) {
                        return@sequence
                    }
                }
                for (childNode in getChildren(top)) {
                    tempNodes.add(childNode)
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
            // cache trackNodes
            val trackNodes = ArrayList<T>(selector.tracks.size)
            val r0 = selector.match(node, this@Transform, trackNodes)
            if (r0 != null) yield(r0)
            getDescendants(node).forEach { childNode ->
                trackNodes.clear()
                val r = selector.match(childNode, this@Transform, trackNodes)
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
            val r0 = selector.matchTracks(node, this@Transform)
            if (r0 != null) yield(r0)
            getDescendants(node).forEach { childNode ->
                val r = selector.matchTracks(childNode, this@Transform)
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