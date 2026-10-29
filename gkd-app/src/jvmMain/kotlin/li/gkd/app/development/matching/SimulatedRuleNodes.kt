package li.gkd.app.development.matching

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchSimulatedNode
import li.gkd.app.matching.diagnostics.MatchSimulation
import li.gkd.app.matching.diagnostics.RuleSelectorDiagnostics
import li.gkd.app.matching.diagnostics.matchRuleSelectorsExplained
import li.gkd.app.matching.diagnostics.recordRuleQuery
import li.gkd.app.matching.query.RuleQueryNodes
import li.gkd.app.matching.query.matchRuleSelectors
import li.gkd.app.matching.query.querySingleSelector
import li.gkd.app.model.ActionResult
import li.gkd.app.rule.ResolvedRule
import li.gkd.selector.NodeAdapter

class SimulatedRuleNodes(
    private val simulation: MatchSimulation,
    private val adapter: NodeAdapter<MatchSimulatedNode>,
    private val work: MatchWork,
    private val diagnostics: MatchDiagnostics,
) : RuleQueryNodes<MatchSimulatedNode> {
    override suspend fun root(): MatchSimulatedNode {
        delay(simulation.rootDelayMillis)
        return simulation.root
    }

    override fun appId(node: MatchSimulatedNode) = simulation.app.id

    override suspend fun refresh(node: MatchSimulatedNode) = node

    override suspend fun match(rule: ResolvedRule, node: MatchSimulatedNode): MatchSimulatedNode? {
        val session = if (diagnostics.enabled) diagnostics.state.value else null
        if (
            session?.recordQueries == true &&
                (session.recordSystem || session.ruleFilter == rule.matchIdentity()) &&
                diagnostics.shouldRecordQuery(session.id)
        ) {
            return recordRuleQuery(
                rule,
                node,
                { simulation.root },
                adapter,
                diagnostics,
                session,
                work.id,
                rootOnlySelectors = false,
            )
        }
        if (
            session?.detailed == true &&
                (session.ruleFilter == null || session.ruleFilter == rule.matchIdentity())
        ) {
            val observer =
                RuleSelectorDiagnostics<MatchSimulatedNode>(
                    diagnostics,
                    session.id,
                    work.id,
                    rule.matchIdentity(),
                )
            return matchRuleSelectorsExplained(
                rule,
                node,
                { simulation.root },
                { target, selector ->
                    querySingleSelector(
                        target,
                        selector,
                        { simulation.root },
                        adapter,
                        rule.matchOptions,
                        rootOnlySelectors = false,
                        observer,
                    )
                },
                observer,
            )
        }
        return matchRuleSelectors(
            rule,
            node,
            { simulation.root },
            { target, selector ->
                querySingleSelector(
                    target,
                    selector,
                    { simulation.root },
                    adapter,
                    rule.matchOptions,
                    rootOnlySelectors = false,
                )
            },
        )
    }

    override suspend fun perform(rule: ResolvedRule, node: MatchSimulatedNode): ActionResult {
        try {
            delay(simulation.actionMillis)
        } finally {
            withContext(NonCancellable) { delay(simulation.cleanupMillis) }
        }
        return ActionResult(rule.parameters.action ?: "click", true)
    }
}
