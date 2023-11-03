package li.songe.selector.util

internal class FilterIndexesSequence<T>(
    private val sequence: Sequence<T>,
    private val indexes: List<Int>,
) : Sequence<T> {
    override fun iterator() = object : Iterator<T> {
        val iterator = sequence.iterator()
        var seqIndex = 0 // sequence
        var i = 0 // indexes
        var nextItem: T? = null

        fun calcNext(): T? {
            if (seqIndex > indexes.last()) return null
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (indexes[i] == seqIndex) {
                    i++
                    seqIndex++
                    return item
                }
                seqIndex++
            }
            return null
        }

        override fun next(): T {
            val result = nextItem
            nextItem = null
            return result ?: calcNext() ?: throw NoSuchElementException()
        }

        override fun hasNext(): Boolean {
            nextItem = nextItem ?: calcNext()
            return nextItem != null
        }
    }
}

internal fun <T> Sequence<T>.filterIndexes(indexes: List<Int>): Sequence<T> {
    return FilterIndexesSequence(this, indexes)
}