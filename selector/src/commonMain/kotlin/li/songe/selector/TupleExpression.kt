package li.songe.selector

data class TupleExpression(
    val numbers: List<Int>,
) : ConnectExpression() {
    override val minOffset = (numbers.firstOrNull() ?: 1) - 1
    override val maxOffset = numbers.lastOrNull()

    private val indexes = numbers.map { x -> x - 1 }
    override fun checkOffset(offset: Int): Boolean {
        return indexes.binarySearch(offset) >= 0
    }

    override fun getOffset(i: Int): Int {
        return numbers[i]
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
