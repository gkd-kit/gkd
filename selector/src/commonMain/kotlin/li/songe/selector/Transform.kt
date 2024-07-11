package li.songe.selector

import kotlin.js.JsExport

@JsExport
class Transform<T> @JsExport.Ignore constructor(
    val getAttr: (Any, String) -> Any?,
    val getInvoke: (Any, String, List<Any>) -> Any? = { _, _, _ -> null },
    val getName: (T) -> CharSequence?,
    val getChildren: (T) -> Sequence<T>,
    val getParent: (T) -> T?,

    val getRoot: (T) -> T? = { node ->
        var parentVar: T? = getParent(node)
        while (parentVar != null) {
            parentVar = getParent(parentVar!!)
        }
        parentVar
    },
    val getDescendants: (T) -> Sequence<T> = { node ->
        sequence { //            深度优先 先序遍历
            //            https://developer.mozilla.org/zh-CN/docs/Web/API/Document/querySelector
            val stack = getChildren(node).toMutableList()
            if (stack.isEmpty()) return@sequence
            stack.reverse()
            val tempNodes = mutableListOf<T>()
            do {
                val top = stack.removeAt(stack.lastIndex)
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

    val traverseChildren: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        getChildren(node).let {
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
    },
    val traverseAncestors: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
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
    val traverseBeforeBrothers: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
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
    val traverseAfterBrothers: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        val parentVal = getParent(node)
        if (parentVal != null) {
            getChildren(parentVal).dropWhile { it != node }.drop(1).let {
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
    val traverseDescendants: (T, ConnectExpression) -> Sequence<T> = { node, connectExpression ->
        sequence {
            val stack = getChildren(node).toMutableList()
            if (stack.isEmpty()) return@sequence
            stack.reverse()
            val tempNodes = mutableListOf<T>()
            var offset = 0
            do {
                val top = stack.removeAt(stack.lastIndex)
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
    @JsExport.Ignore
    val querySelectorAll: (T, Selector) -> Sequence<T> = { node, selector ->
        sequence {
            selector.match(node, this@Transform)?.let { yield(it) }
            getDescendants(node).forEach { childNode ->
                selector.match(childNode, this@Transform)?.let { yield(it) }
            }
        }
    }
    val querySelector: (T, Selector) -> T? = { node, selector ->
        querySelectorAll(node, selector).firstOrNull()
    }

    @JsExport.Ignore
    val querySelectorAllContext: (T, Selector) -> Sequence<Context<T>> = { node, selector ->
        sequence {
            selector.matchContext(node, this@Transform)?.let { yield(it) }
            getDescendants(node).forEach { childNode ->
                selector.matchContext(childNode, this@Transform)?.let { yield(it) }
            }
        }
    }

    val querySelectorContext: (T, Selector) -> Context<T>? = { node, selector ->
        querySelectorAllContext(node, selector).firstOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    val querySelectorAllArray: (T, Selector) -> Array<T> = { node, selector ->
        val result = querySelectorAll(node, selector).toList()
        (result as List<Any>).toTypedArray() as Array<T>
    }

    val querySelectorAllContextArray: (T, Selector) -> Array<Context<T>> = { node, selector ->
        val result = querySelectorAllContext(node, selector)
        result.toList().toTypedArray()
    }

    companion object {
        fun <T> multiplatformBuild(
            getAttr: (Any, String) -> Any?,
            getInvoke: (Any, String, List<Any>) -> Any?,
            getName: (T) -> String?,
            getChildren: (T) -> Array<T>,
            getParent: (T) -> T?,
        ): Transform<T> {
            return Transform(
                getAttr = getAttr,
                getInvoke = { target, name, args -> getInvoke(target, name, args) },
                getName = getName,
                getChildren = { node -> getChildren(node).asSequence() },
                getParent = getParent,
            )
        }
    }
}