package li.gkd.app.matching.query

import li.gkd.app.rule.ResolvedRule
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.SelectorObserver

/** Root-only Android selectors skip descendants; other queries try self before descendants. */
inline fun <N : Any> querySingleSelector(
    node: N,
    selector: Selector,
    root: () -> N?,
    adapter: NodeAdapter<N>,
    options: MatchOptions,
    rootOnlySelectors: Boolean,
    observer: SelectorObserver<N>? = null,
): N? {
    val rootOnly = rootOnlySelectors && selector.isMatchRoot
    val target = if (rootOnly) root() ?: return null else node
    val result = selector.match(target, adapter, options, observer)
    if (result != null || rootOnly) return result
    return adapter.querySelector(target, selector, options, observer)
}

/** The same selector combination is used by Android nodes and simulated trees. */
fun <N : Any> matchRuleSelectors(
    rule: ResolvedRule,
    node: N,
    root: () -> N?,
    query: (N, Selector) -> N?,
    explain: ((String) -> Unit)? = null,
): N? =
    matchRuleSelectorsUsing(
        rule.matchRoot,
        rule.anyMatches,
        rule.matches,
        rule.excludeMatches,
        rule.excludeAllMatches,
        node,
        root,
        { target, selector, _, _ -> query(target, selector) },
        explain,
    )

/** Selector grouping semantics shared by ordinary queries and diagnostics. */
inline fun <N : Any> matchRuleSelectorsUsing(
    matchRoot: Boolean,
    anyMatches: List<Selector>,
    matches: List<Selector>,
    excludeMatches: List<Selector>,
    excludeAllMatches: List<Selector>,
    node: N,
    root: () -> N?,
    query: (N, Selector, String, Int) -> N?,
    noinline explain: ((String) -> Unit)?,
): N? {
    val targetRoot = if (matchRoot) root() ?: return null else node
    var result: N? = null
    if (anyMatches.isNotEmpty()) {
        for ((index, selector) in anyMatches.withIndex()) {
            result = query(targetRoot, selector, "anyMatches", index)
            if (explain != null)
                explain("anyMatches[$index]:${if (result == null) "miss" else "hit"}")
            if (result != null) break
        }
        if (result == null) return null
    }
    for ((index, selector) in matches.withIndex()) {
        result = query(targetRoot, selector, "matches", index)
        if (explain != null) explain("matches[$index]:${if (result == null) "miss" else "hit"}")
        if (result == null) return null
    }
    for ((index, selector) in excludeMatches.withIndex()) {
        val excluded = query(targetRoot, selector, "excludeMatches", index) != null
        if (explain != null) explain("excludeMatches[$index]:${if (excluded) "hit" else "miss"}")
        if (excluded) return null
    }
    // Preserve the existing production semantics: any hit in excludeAllMatches rejects.
    for ((index, selector) in excludeAllMatches.withIndex()) {
        val excluded = query(targetRoot, selector, "excludeAllMatches", index) != null
        if (explain != null) explain("excludeAllMatches[$index]:${if (excluded) "hit" else "miss"}")
        if (excluded) return null
    }
    return result
}
