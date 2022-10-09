package li.songe.node_selector.selector

data class Position(val x: NumberUnit, val y: NumberUnit) {
    override fun toString() = "(${x}, ${y})"

    data class NumberUnit(val number: Number, val unit: Unit) {
        override fun toString() = number.toString() + unit.toString()
    }

    sealed class Unit(private val key: String) {
        override fun toString() = key
        object Percentage : Unit("%")
    }
}
