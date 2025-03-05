package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class QueryContext<T>(
    val current: T,
    val prev: QueryContext<T>? = null,
    val matched: Boolean = true,
) {
    @Suppress("unused")
    val originalContext: QueryContext<T>
        get() {
            var context = this
            while (context.prev != null) {
                context = context.prev
            }
            return context
        }

    fun getPrev(index: Int): QueryContext<T>? {
        if (index < 0) return null
        var context = prev ?: return null
        repeat(index) {
            context = context.prev ?: return null
        }
        return context
    }

    fun get(index: Int): QueryContext<T> {
        if (index == 0) return this
        return getPrev(index - 1) ?: throw IndexOutOfBoundsException()
    }

    fun toList(): List<T> {
        val list = mutableListOf(this.current)
        var context = prev
        while (context != null) {
            list.add(context.current)
            context = context.prev
        }
        return list
    }

    fun toContextList(): List<QueryContext<T>> {
        val list = mutableListOf(this)
        var context = prev
        while (context != null) {
            list.add(context)
            context = context.prev
        }
        return list
    }

    @Suppress("UNCHECKED_CAST", "unused")
    fun toArray(): Array<T> {
        return (toList() as List<Any>).toTypedArray() as Array<T>
    }

    fun next(value: T): QueryContext<T> {
        return QueryContext(value, this)
    }

    fun mismatch(): QueryContext<T> {
        return copy(matched = false)
    }
}
