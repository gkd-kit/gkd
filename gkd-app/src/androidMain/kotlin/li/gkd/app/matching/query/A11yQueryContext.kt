package li.gkd.app.matching.query

import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import li.gkd.app.META
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchNodeMetrics
import li.gkd.app.matching.diagnostics.NodeCacheKind
import li.gkd.app.matching.diagnostics.NodeReadOperation
import li.gkd.app.matching.diagnostics.RuleSelectorDiagnostics
import li.gkd.app.matching.input.appChangeTime
import li.gkd.app.matching.input.currentTopActivity
import li.gkd.app.matching.input.foregroundRulesFlow
import li.gkd.app.priv.toHidden
import li.gkd.app.rule.ResolvedRule
import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.TraversalCandidate
import li.gkd.selector.relation.RelationExpression

class A11yQueryContext(
    private val getRoot: () -> AccessibilityNodeInfo?,
    private val interruptable: Boolean = true,
    private val metrics: MatchNodeMetrics? = null,
    private val diagnostics: MatchDiagnostics? = null,
    private val nodeReader: MatchNodeReader? = null,
) {
    private inline fun <T> measure(operation: NodeReadOperation, args: () -> List<Any> = { emptyList() }, noinline block: () -> T): T {
        val trace = diagnostics
        val runId = queryRunId
        val rule = if (trace?.enabled == true) currentRule?.matchIdentity() ?: "manual" else null
        val page = queryPage
        val collector = metrics
        val observation = li.gkd.app.matching.diagnostics.MatchNodeObservation.current()
        val arguments = if (trace?.enabled == true) args().map { value ->
            observation?.value?.invoke(value) ?: when (value) {
                is Int -> li.gkd.app.matching.diagnostics.MatchRecordedValue("int", value.toString())
                is String -> li.gkd.app.matching.diagnostics.MatchRecordedValue("string", value)
                else -> li.gkd.app.matching.diagnostics.MatchRecordedValue("node_identity", reference = System.identityHashCode(value))
            }
        } else emptyList()
        val read = if (collector == null) block else { { collector.measure(operation, block) } }
        val result = if (nodeReader != null) {
            nodeReader.read(name = operation.name, runId = runId, rule = rule, page = page, arguments = arguments, block = read)
        } else if (trace?.enabled == true) trace.observeOperation(operation.name, runId, rule, page,
            parentOperationId = observation?.operation?.id, arguments = arguments,
            sessionId = observation?.sessionId, block = read)
        else read()
        guardInterrupt()
        return result
    }
    fun querySelfOrSelector(
        node: AccessibilityNodeInfo,
        selector: Selector,
        options: MatchOptions,
        diagnostics: MatchDiagnostics? = null,
    ): AccessibilityNodeInfo? {
        if (diagnostics != null) {
            val session = if (diagnostics.enabled) diagnostics.state.value else null
            if (session?.recordSystem == true && diagnostics.shouldRecordQuery(session.id)) {
                return li.gkd.app.matching.diagnostics.recordManualQuery(selector, options, node, ::requireQueryRoot, adapter, diagnostics, session, queryRunId)
            }
        }
        return querySingleSelector(node, selector, ::requireQueryRoot, adapter, options, rootOnlySelectors = true)
    }

    fun queryRule(
        rule: ResolvedRule,
        node: AccessibilityNodeInfo,
        diagnostics: MatchDiagnostics,
    ): AccessibilityNodeInfo? {
        currentRule = rule
        try {
            val session = if (diagnostics.enabled) diagnostics.state.value else null
            if (session?.recordQueries == true && (session.recordSystem || session.ruleFilter == rule.matchIdentity()) && diagnostics.shouldRecordQuery(session.id)) {
                return li.gkd.app.matching.diagnostics.recordRuleQuery(rule, node, ::requireQueryRoot, adapter,
                    diagnostics, session, queryRunId, rootOnlySelectors = true)
            }
            if (session?.detailed == true && (session.ruleFilter == null || session.ruleFilter == rule.matchIdentity())) {
                val observer = RuleSelectorDiagnostics<AccessibilityNodeInfo>(diagnostics, session.id, queryRunId, rule.matchIdentity())
                return li.gkd.app.matching.diagnostics.matchRuleSelectorsExplained(rule, node, ::requireQueryRoot, { target, selector ->
                    querySingleSelector(target, selector, ::requireQueryRoot, adapter, rule.matchOptions, rootOnlySelectors = true, observer)
                }, observer)
            }
            return li.gkd.app.matching.query.matchRuleSelectors(
                rule, node, ::requireQueryRoot,
                { target, selector -> querySelfOrSelector(target, selector, rule.matchOptions) },
            )
        } finally {
            currentRule = null
        }
    }
}
