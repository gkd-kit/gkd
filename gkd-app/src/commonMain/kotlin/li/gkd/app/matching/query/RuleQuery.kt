package li.gkd.app.matching.query

import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.model.ActionResult
import li.gkd.app.rule.AppRule
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.rule.RuleStatus

/** Platform operations for a single query; N is the original node, never a wrapper tree. */
interface RuleQueryNodes<N : Any> {
    suspend fun root(): N?

    fun appId(node: N): String?

    suspend fun refresh(node: N): N?

    suspend fun match(rule: ResolvedRule, node: N): N?

    suspend fun perform(rule: ResolvedRule, node: N): ActionResult
}

/** Shared production rule traversal, also used by simulated nodes. */
suspend fun <N : Any> queryRules(
    work: MatchWork,
    activity: ForegroundRuleSnapshot,
    nodes: RuleQueryNodes<N>,
    source: N?,
    forced: Boolean,
    isCurrent: () -> Boolean,
    schedule: (ResolvedRule, RuleDelayKind, Long) -> Unit,
    correctApp: (String) -> Unit,
    completed: (ResolvedRule, N, ActionResult) -> Unit,
    execute: Boolean = true,
    observed: ((ResolvedRule, String, Boolean, ActionResult?) -> Unit)? = null,
) {
    for (rule in activity.currentRules) {
        if (rule.status == RuleStatus.MatchDelay) {
            schedule(rule, RuleDelayKind.Match, rule.matchDelay)
        }
    }
    if (activity.skipMatch) {
        work.trace(MatchStage.Rule, "no_eligible_rules")
        activity.currentRules.forEach {
            work.trace(MatchStage.Rule, it.status.name, it)
            observed?.invoke(it, it.status.name, false, null)
        }
        return
    }
    var sourceNode = source
    var sourceUsed = false
    for (rule in activity.priorityRules) {
        work.selectRule(rule)
        if (!isCurrent()) {
            work.trace(MatchStage.Rule, "query_context_changed", rule)
            return
        }
        val status = rule.status
        if (status != RuleStatus.Ready) {
            work.trace(MatchStage.Rule, status.name, rule)
            observed?.invoke(rule, status.name, false, null)
            continue
        }
        if (forced && !rule.checkForced()) {
            work.trace(MatchStage.Rule, "forced_window_expired", rule)
            continue
        }
        sourceNode?.let { if (sourceUsed) sourceNode = nodes.refresh(it) }
        sourceUsed = true
        work.trace(MatchStage.Root, "reading_node", rule)
        val node = sourceNode ?: nodes.root()
        work.ensureCurrent()
        if (node == null) {
            work.trace(MatchStage.Root, "node_unavailable", rule)
            observed?.invoke(rule, "node_unavailable", false, null)
            continue
        }
        val appId =
            nodes.appId(node)
                ?: run {
                    work.trace(MatchStage.Root, "node_package_unavailable", rule)
                    return
                }
        val matchesApp = rule.matchActivity(appId)
        if (activity.topActivity.appId != appId || (!matchesApp && rule is AppRule)) {
            work.trace(MatchStage.Root, "foreground_conflict", rule)
            observed?.invoke(rule, "foreground_conflict", false, null)
            correctApp(appId)
            return
        }
        if (!matchesApp) {
            work.trace(MatchStage.Rule, "app_scope_excluded", rule)
            continue
        }
        work.trace(MatchStage.Selector, "matching", rule)
        val target =
            try {
                work.match(rule) { nodes.match(rule, node) }
            } catch (error: MatchQueryIncomplete) {
                work.ensureCurrent()
                work.trace(MatchStage.Selector, error.reason.wireValue, rule)
                observed?.invoke(rule, error.reason.wireValue, false, null)
                continue
            }
        work.ensureCurrent()
        if (target == null) {
            work.trace(MatchStage.Selector, "not_matched", rule)
            observed?.invoke(rule, "not_matched", false, null)
            continue
        }
        work.trace(MatchStage.Selector, "matched", rule)
        if (!execute) {
            observed?.invoke(rule, "matched_without_execution", true, null)
            continue
        }
        if (rule.checkDelay()) {
            work.trace(MatchStage.Rule, "action_delay", rule)
            schedule(rule, RuleDelayKind.Action, rule.actionDelay)
            continue
        }
        if (rule.status != RuleStatus.Ready || !isCurrent()) {
            work.trace(MatchStage.Rule, "action_precondition_changed", rule)
            return
        }
        work.ensureCurrent()
        work.trace(MatchStage.Action, "action_started", rule)
        val result = work.performAction(rule) { nodes.perform(rule, target) }
        work.trace(MatchStage.Action, result.outcomeReason, rule, action = result)
        observed?.invoke(
            rule,
            if (result.coordinateError != null) result.outcomeReason else "action_finished",
            true,
            result,
        )
        // Preserve an acknowledged physical result even if it caused a page transition.
        // The host commits counters only while holding its activity-state lock.
        if (result.result) completed(rule, target, result)
        work.ensureCurrent()
    }
}
