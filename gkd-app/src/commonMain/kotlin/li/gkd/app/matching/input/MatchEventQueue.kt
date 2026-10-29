package li.gkd.app.matching.input

/** Bounded platform references; input ownership is released on drop. */
class MatchEventQueue<T : Any>(
    private val capacity: Int,
    private val key: (T) -> MatchEventKey,
    private val discard: (T) -> Unit,
    private val overflow: ((T, Int) -> Unit)? = null,
) {
    private val queue = ArrayDeque<T>()

    init {
        require(capacity > 0)
    }

    @Synchronized fun firstOrNull(): T? = queue.firstOrNull()

    @Synchronized fun isNotEmpty() = queue.isNotEmpty()

    @Synchronized
    fun offer(value: T) {
        if (queue.size == capacity) {
            val removed = queue.removeFirst()
            try {
                overflow?.invoke(removed, capacity)
            } finally {
                discard(removed)
            }
        }
        queue.addLast(value)
    }

    @Synchronized
    fun takeBatch(head: T): List<T> {
        if (queue.firstOrNull() !== head) return emptyList()
        val batch = queue.takeWhile { key(it) == key(head) }
        repeat(batch.size) { queue.removeFirst() }
        return batch
    }

    @Synchronized
    fun clear() {
        queue.forEach(discard)
        queue.clear()
    }
}
