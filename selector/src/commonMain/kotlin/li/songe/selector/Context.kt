package li.songe.selector

import kotlin.js.JsExport

@JsExport
data class Context<T>(
    val current: T,
    val prev: Context<T>? = null,
) {
    fun getPrev(index: Int): Context<T>? {
        if (index < 0) return null
        var context = prev ?: return null
        repeat(index) {
            context = context.prev ?: return null
        }
        return context
    }

    fun get(index: Int): Context<T> {
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

    @Suppress("UNCHECKED_CAST")
    fun toArray(): Array<T> {
        return (toList() as List<Any>).toTypedArray() as Array<T>
    }

    fun next(value: T): Context<T> {
        return Context(value, this)
    }
}
