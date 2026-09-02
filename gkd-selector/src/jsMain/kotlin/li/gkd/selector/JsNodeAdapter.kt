@file:Suppress(
    "UNUSED_PARAMETER",
    "UNUSED_TYPE_PARAMETER",
    "UNUSED_VARIABLE",
)

package li.gkd.selector

import kotlin.js.collections.JsArray
import kotlin.js.collections.toList

@JsName("Iterable")
public external interface JsIterable<out T>

private fun <T> Iterable<T>.toJsArray(): JsArray<T> {
    val result = JsArray<T>()
    for (value in this) result.asDynamic().push(value)
    return result
}

private fun <T> Sequence<T>.toJsIterable(): JsIterable<T> {
    val createIterator = { iterator() }
    val iteratorHasNext = { iterator: Iterator<T> -> iterator.hasNext() }
    val iteratorNext = { iterator: Iterator<T> -> iterator.next() }
    return js(
        """
        ({
            [Symbol.iterator]: function*() {
                var iterator = createIterator();
                while (iteratorHasNext(iterator)) {
                    yield iteratorNext(iterator);
                }
            }
        })
        """,
    ).unsafeCast<JsIterable<T>>()
}

private fun <T> JsIterable<T>.toIterator(): Iterator<T> {
    val iterable = this
    val jsIterator = js("iterable[Symbol.iterator]()")
    return object : Iterator<T> {
        private var nextResult: dynamic = null
        private var nextLoaded = false

        override fun hasNext(): Boolean {
            if (!nextLoaded) {
                nextResult = jsIterator.next()
                nextLoaded = true
            }
            return nextResult.done != true
        }

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            nextLoaded = false
            return nextResult.value.unsafeCast<T>()
        }
    }
}

@JsExport
public abstract class JsNodeAdapter<T : Any> {
    public abstract fun getAttr(target: Any, name: String): Any?

    public open fun getInvoke(target: Any, name: String, args: JsArray<Any>): Any? = null

    public abstract fun getName(node: T): String?

    public abstract fun getChildCount(node: T): Int

    public abstract fun getChild(node: T, index: Int): T?

    public abstract fun getParent(node: T): T?

    public abstract fun getNodeKey(node: T): Any

    private fun checkedNodeKey(node: T): Any {
        val key: dynamic = getNodeKey(node)
        require(key != null) {
            "JsNodeAdapter.getNodeKey must return a non-null value"
        }
        return key.unsafeCast<Any>()
    }

    public open fun getRoot(node: T): T? {
        val visitedKeys = mutableSetOf<Any>()
        var current = node
        while (visitedKeys.add(checkedNodeKey(current))) {
            current = getParent(current) ?: return current
        }
        return null
    }

    /**
     * Returns every matching descendant once according to [getNodeKey]. The result may use an
     * implementation-specific order, so fast queries may change query order and the first match.
     * Selector matching validates every returned candidate again.
     */
    public open fun getFastQueryDescendants(
        node: T,
        fastQueryList: JsArray<FastQuery>,
    ): JsIterable<T> {
        val fastQueries = fastQueryList.toList()
        return core.getDescendants(node).filter { candidate ->
            fastQueries.any { query ->
                query.acceptValue(getAttr(candidate, query.attributeName))
            }
        }.toJsIterable()
    }

    private val core = object : NodeAdapter<T>() {
        override fun getAttr(target: Any, name: String): Any? =
            this@JsNodeAdapter.getAttr(target, name)

        override fun getInvoke(target: Any, name: String, args: List<Any>): Any? =
            this@JsNodeAdapter.getInvoke(target, name, args.toJsArray())

        override fun getName(node: T): String? = this@JsNodeAdapter.getName(node)

        override fun getChildCount(node: T): Int = this@JsNodeAdapter.getChildCount(node)

        override fun getChild(node: T, index: Int): T? = this@JsNodeAdapter.getChild(node, index)

        override fun getParent(node: T): T? = this@JsNodeAdapter.getParent(node)

        override fun getNodeKey(node: T): Any = this@JsNodeAdapter.checkedNodeKey(node)

        override fun getRoot(node: T): T? = this@JsNodeAdapter.getRoot(node)

        override fun getFastQueryDescendants(
            node: T,
            fastQueryList: List<FastQuery>,
        ): Sequence<T> = Sequence {
            this@JsNodeAdapter
                .getFastQueryDescendants(node, fastQueryList.toJsArray())
                .toIterator()
        }
    }

    public fun match(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): T? = selector.match(node, core, options)

    public fun matchWithTrace(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): SelectorMatch<T>? = selector.matchWithTrace(node, core, options)

    public fun querySelector(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): T? = core.querySelector(node, selector, options)

    public fun querySelectorAll(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): JsArray<T> = core.querySelectorAll(node, selector, options).toJsArray()

    public fun querySelectorWithTrace(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): SelectorMatch<T>? = core.querySelectorWithTrace(node, selector, options)

    public fun querySelectorAllWithTrace(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): JsArray<SelectorMatch<T>> =
        core.querySelectorAllWithTrace(node, selector, options).toJsArray()
}
