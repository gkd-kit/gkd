package li.songe.selector

sealed class ConnectExpression {
    abstract val minOffset: Int
    abstract val maxOffset: Int?
    abstract fun checkOffset(offset: Int): Boolean
    abstract fun getOffset(i: Int): Int
}
