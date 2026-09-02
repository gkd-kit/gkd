package li.gkd.selector

internal class MatchContext<T : Any>(
    val current: T,
    val prev: MatchContext<T>? = null,
    val incomingOffset: Int = -1,
) {
    fun getPrev(index: Int): MatchContext<T>? {
        if (index < 0) return null
        var context = prev ?: return null
        repeat(index) {
            context = context.prev ?: return null
        }
        return context
    }

    fun get(index: Int): MatchContext<T> {
        if (index == 0) return this
        return getPrev(index - 1) ?: throw IndexOutOfBoundsException()
    }

    fun toContextList(): List<MatchContext<T>> {
        val list = mutableListOf(this)
        var context = prev
        while (context != null) {
            list.add(context)
            context = context.prev
        }
        return list
    }

    fun next(value: T, offset: Int): MatchContext<T> {
        return MatchContext(value, this, offset)
    }
}
