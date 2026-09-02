package li.gkd.selector

internal enum class LogicalOperator(val key: String, val precedence: Int) {
    And("&&", 2),
    Or("||", 1),
    ;

    companion object {
        val parseOrder: List<LogicalOperator> by lazy {
            entries.sortedByDescending { it.key.length }
        }
    }
}
