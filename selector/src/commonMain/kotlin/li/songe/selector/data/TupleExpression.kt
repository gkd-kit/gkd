package li.songe.selector.data

import li.songe.selector.NodeSequenceFc
import li.songe.selector.util.filterIndexes

data class TupleExpression(
    val numbers: List<Int>,
) : ConnectExpression() {
    override val isConstant = numbers.size == 1
    override val minOffset = (numbers.firstOrNull() ?: 1) - 1
    private val indexes = numbers.map { x -> x - 1 }
    override val traversal: NodeSequenceFc = object : NodeSequenceFc {
        override fun <T> invoke(sq: Sequence<T?>): Sequence<T?> {
            return sq.filterIndexes(indexes)
        }
    }

    override fun toString(): String {
        if (numbers.size == 1) {
            return if (numbers.first() == 1) {
                ""
            } else {
                numbers.first().toString()
            }
        }
        return "(${numbers.joinToString(",")})"
    }
}
