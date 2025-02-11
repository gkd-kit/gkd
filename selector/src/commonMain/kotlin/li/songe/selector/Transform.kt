package li.songe.selector

import li.songe.selector.connect.ConnectExpression
import kotlin.js.JsExport
import kotlin.sequences.forEach

@Suppress("unused")
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
            parentVar = getParent(parentVar)
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
                parentVar.let {
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

    val traverseFastQueryDescendants: (T, List<FastQuery>) -> Sequence<T> = { _, _ -> emptySequence() }
) {

    @JsExport.Ignore
    fun querySelectorAll(
        node: T,
        selector: Selector,
        option: MatchOption = MatchOption.default,
    ): Sequence<T> = sequence {
        (if (option.fastQuery && selector.fastQueryList.isNotEmpty()) {
            traverseFastQueryDescendants(node, selector.fastQueryList)
        } else {
            getDescendants(node)
        }).forEach { childNode ->
            selector.match(childNode, this@Transform, option)?.let { yield(it) }
        }
    }

    fun querySelector(
        node: T,
        selector: Selector,
        option: MatchOption = MatchOption.default,
    ): T? {
        return querySelectorAll(node, selector, option).firstOrNull()
    }

    @JsExport.Ignore
    fun querySelectorAllContext(
        node: T,
        selector: Selector,
        option: MatchOption = MatchOption.default,
    ): Sequence<QueryResult<T>> {
        return sequence {
            getDescendants(node).forEach { childNode ->
                selector.matchContext(childNode, this@Transform, option).let {
                    if (it.context.matched) {
                        yield(it)
                    }
                }
            }
        }
    }

    fun querySelectorContext(
        node: T,
        selector: Selector,
        option: MatchOption = MatchOption.default,
    ): QueryResult<T>? {
        return querySelectorAllContext(node, selector, option).firstOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    fun querySelectorAllArray(
        node: T,
        selector: Selector,
        option: MatchOption = MatchOption.default,
    ): Array<T> {
        val result = querySelectorAll(node, selector, option).toList()
        return (result as List<Any>).toTypedArray() as Array<T>
    }

    fun querySelectorAllContextArray(
        node: T,
        selector: Selector,
        option: MatchOption = MatchOption.default,
    ): Array<QueryResult<T>> {
        return querySelectorAllContext(node, selector, option).toList().toTypedArray()
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