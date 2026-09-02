package li.gkd.selector.relation

public sealed interface RelationExpression {
    public val minOffset: Int
    public val maxOffset: Int?
    public val matchesAllOffsets: Boolean
        get() = false

    public fun checkOffset(offset: Int): Boolean
}

internal class TupleExpression(
    val numbers: List<Int>,
) : RelationExpression {
    override val minOffset = (numbers.firstOrNull() ?: 1) - 1
    override val maxOffset = numbers.lastOrNull()?.minus(1)

    private val indexes = numbers.map { it - 1 }

    override fun checkOffset(offset: Int): Boolean = indexes.binarySearch(offset) >= 0
}

/** Represents the sequence `an+b`. */
internal class PolynomialExpression(
    val a: Int = 0,
    val b: Int = 1,
) : RelationExpression {
    override val matchesAllOffsets: Boolean
        get() = a == 1 && b == 0

    companion object {
        fun isValid(a: Int, b: Int): Boolean = when {
            a > 0 -> true
            a == 0 -> b > 0
            else -> b > 0 && b.toLong() > -a.toLong()
        }
    }

    override val minOffset = (when {
        a > 0 && b > 0 -> a + b
        a > 0 && b == 0 -> a
        a > 0 -> {
            // 2n-10 -> n>=6
            // 3n-10 -> n>=4
            // 3n-3 -> n>=2
            // 3n-1 -> n>=1
            // an+b>0 -> n>-b/a
            val minN = -b / a + 1
            a * minN + b
        }

        a == 0 -> b
        else -> {
            // -2n+9 -> (1_7,2_5,3_3,4_1) -> (1,3,5,7) -> 1
            // -3n+9 -> (1_6,2_3) -> (3,6)
            // -5n+7 -> (1_2) -> (2)
            val maxN = -b / a - if (b % a == 0) 1 else 0
            a * maxN + b
        }
    }) - 1

    override val maxOffset = (when {
        a > 0 -> null
        a == 0 -> b
        else -> a + b
    })?.let { it - 1 }

    private val isConstant = minOffset == maxOffset

    // (2n-1) -> (1,3,5) -> [0,2,4]
    override fun checkOffset(offset: Int): Boolean {
        if (isConstant) return offset == minOffset
        val y = offset + 1 - b
        return y % a == 0 && y / a >= 1
    }
}
