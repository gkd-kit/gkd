package li.gkd.selector

/** Observes only conditions actually evaluated. No node/value is read again for explanation. */
public interface SelectorObserver<T : Any> {
    public val enabled: Boolean

    public fun record(event: SelectorEvaluation<T>)
}

/** Diagnostic event kinds with stable names for external output. */
public enum class SelectorEvaluationKind(public val key: String) {
    Name("name"),
    Comparison("comparison"),
    Logical("logical"),
    Skipped("skipped"),
    FastQueryScope("fast_query_scope"),
    NoCandidates("no_candidates"),
    SelectorShortCircuit("selector_short_circuit"),
    SelectorNot("selector_not"),
    CachedFailure("cached_failure"),
    RelationExhausted("relation_exhausted"),
    RelationSearch("relation_search"),
    LimitReached("limit_reached"),
}

public data class SelectorEvaluation<T : Any>(
    public val node: T,
    /** Unit index, or null when the event has no specific unit. */
    public val unit: Int? = null,
    /** Property selector index, or null when the event has no specific property selector. */
    public val property: Int? = null,
    public val kind: SelectorEvaluationKind,
    public val expression: String,
    public val matched: Boolean?,
    public val left: Any? = null,
    public val right: Any? = null,
    public val operator: String? = null,
)
