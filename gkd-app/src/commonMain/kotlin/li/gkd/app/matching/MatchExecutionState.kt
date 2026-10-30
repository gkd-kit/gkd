package li.gkd.app.matching

/** Inputs captured by the host at the point where a query may execute an action. */
data class MatchExecutionState(
    val page: Any,
    val serviceEffective: Boolean,
    val interactive: Boolean,
    val foregroundPending: Boolean,
    val windowKnown: Boolean,
    val matchingEnabled: Boolean,
) {
    fun permits(queryPage: Any): Boolean =
        page === queryPage &&
            serviceEffective &&
            interactive &&
            !foregroundPending &&
            windowKnown &&
            matchingEnabled
}
