package li.songe.selector.data

import li.songe.selector.NodeSequenceFc
import li.songe.selector.util.filterIndexes

/**
 * an+b
 */
data class PolynomialExpression(val a: Int = 0, val b: Int = 1) : ConnectExpression() {

    override fun toString(): String {
        if (a == 0 && b == 0) return "0"
        if (a == 1 && b == 1) return "(n+1)"
        if (b == 0) {
            if (a == 1) return "n"
            return if (a > 0) {
                "${a}n"
            } else {
                "(${a}n)"
            }
        }
        if (a == 0) {
            if (b == 1) return ""
            return if (b > 0) {
                b.toString()
            } else {
                "(${b})"
            }
        }
        val bOp = if (b >= 0) "+" else ""
        return "(${a}n${bOp}${b})"
    }

    val numbers = if (a < 0) {
        if (b < 0) {
            emptyList()
        } else if (b > 0) {
            if (b <= -a) {
                emptyList()
            } else {
                val list = mutableListOf<Int>()
                var n = 1
                while (a * n + b > 0) {
                    list.add(a * n + b)
                    n++
                }
                list.sorted()
            }
        } else {
            emptyList()
        }
    } else if (a > 0) {
        // infinite
        emptyList()
    } else {
        if (b < 0) {
            emptyList()
        } else if (b > 0) {
            listOf(b)
        } else {
            emptyList()
        }
    }

    override val isConstant = numbers.size == 1
    override val minOffset = (numbers.firstOrNull() ?: 1) - 1
    private val b1 = b - 1
    private val indexes = numbers.map { x -> x - 1 }

    override val traversal = object : NodeSequenceFc {
        override fun <T> invoke(sq: Sequence<T?>): Sequence<T?> {
            return if (a > 0) {
                sq.filterIndexed { x, _ -> (x - b1) % a == 0 && (x - b1) / a > 0 }
            } else {
                sq.filterIndexes(indexes)
            }
        }
    }

}
