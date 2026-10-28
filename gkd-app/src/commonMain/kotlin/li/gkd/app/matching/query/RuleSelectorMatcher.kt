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
