package li.gkd.app.matching.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import li.gkd.app.development.matching.evaluateDebugRule
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest
import li.gkd.app.matching.diagnostics.MatchDiagnosticCapture
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.recordRuleQuery
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.model.ScreenSize
import li.gkd.app.rule.AppRule
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleStatus
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.PositionCalculationException
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.RuleBounds
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem
import li.gkd.selector.NodeAdapter

class RuleQueryTest {
    /** An unvisited excluded node must not become a false negative or allow an action. */
    @Test
    fun incompleteExclusionAbortsAutomaticAndDebugActionsAndCanBeRetried() = runTest {
        for (part in listOf("excludeMatches", "excludeAllMatches")) for (record in
            listOf(false, true)) {
            val rule = rule({ 10_000 }, "$part:'[text=\"blocked\"]'")
            val diagnostics = MatchDiagnostics()
            val session =
                if (record)
                    diagnostics.start(
                        MatchTimingPolicy(),
                        ruleFilter = rule.matchIdentity(),
                        recordQueries = true,
                    )
                else null
            val work = MatchWork(1, MatchTimingPolicy(), diagnostics) { true }
            var children = listOf(1, 2, 3)
            val adapter =
                object : NodeAdapter<Int>() {
                    override fun getAttr(target: Any, name: String): Any? =
                        if (name == "text") "safe" else null

                    override fun getName(node: Int) = "Node"

                    override fun getChildCount(node: Int) = if (node == 0) children.size else 0

                    override fun getChild(node: Int, index: Int) =
                        if (node == 0) children.getOrNull(index) else null

                    override fun getParent(node: Int): Int? = null

                    override fun getNodeKey(node: Int): Any = node

                    override fun getDescendants(node: Int) =
                        children
                            .asSequence()
                            .withQueryLimit(2, MatchQueryIncompleteReason.DescendantLimit)
                }
            var actions = 0
            var commits = 0
            val nodes =
                object : RuleQueryNodes<Int> {
                    override suspend fun root() = 0

                    override fun appId(node: Int) = "app.test"

                    override suspend fun refresh(node: Int) = node

                    override suspend fun match(rule: ResolvedRule, node: Int): Int? =
                        if (session != null) {
                            recordRuleQuery(
                                rule,
                                node,
                                { 0 },
                                adapter,
                                diagnostics,
                                session,
                                work.id,
                                rootOnlySelectors = false,
                            )
                        } else
                            matchRuleSelectors(
                                rule,
                                node,
                                { 0 },
                                { target, selector ->
                                    selector.match(target, adapter)
                                        ?: adapter.querySelector(target, selector)
                                },
                            )

                    override suspend fun perform(rule: ResolvedRule, node: Int): ActionResult {
                        actions++
                        return ActionResult("click", true)
                    }
                }
            val request = MatchDebugRunRequest("test", "", "", true)
            assertEquals(
                "query_descendant_limit",
                evaluateDebugRule(work, request, rule, "app.test", null, nodes).reason,
            )
            suspend fun automaticQuery() =
                queryRules(
                    work,
                    ForegroundRuleSnapshot(
                        TopActivity("app.test", null),
                        false,
                        RuleSummary(appIdToRules = mapOf("app.test" to listOf(rule))),
                    ),
                    nodes,
                    null,
                    false,
                    { true },
                    { _, _, _ -> },
                    {},
                    { _, _, _ -> commits++ },
                )
            automaticQuery()
            assertEquals(0, actions)
            assertEquals(0, commits)
            assertEquals(RuleStatus.Ready, rule.status)
            if (session != null) {
                val capture = diagnostics.export(session.id)
                kotlin.test.assertTrue(capture.events.any { it.reason == "query_descendant_limit" })
                capture.queryRecordings.forEach {
                    assertEquals(MatchQueryIncomplete::class.java.name, it.failure)
                }
            }
            children = listOf(1, 2) // Exactly at the limit is complete; a later query can act.
            automaticQuery()
            assertEquals(1, actions)
            assertEquals(1, commits)
        }
    }

    @Test
    fun queryBudgetAllowsShortCircuitAndExactExhaustionButRejectsTruncation() {
        assertEquals(
            1,
            sequenceOf(1, 2, 3)
                .withQueryLimit(2, MatchQueryIncompleteReason.DescendantLimit)
                .first(),
        )
        assertEquals(
            listOf(1, 2),
            sequenceOf(1, 2).withQueryLimit(2, MatchQueryIncompleteReason.DescendantLimit).toList(),
        )
        assertEquals(
            MatchQueryIncompleteReason.DescendantLimit,
            kotlin.test
                .assertFailsWith<MatchQueryIncomplete> {
                    sequenceOf(1, 2, 3)
                        .withQueryLimit(2, MatchQueryIncompleteReason.DescendantLimit)
                        .toList()
                }
                .reason,
        )
    }

    /**
     * Coordinate failure reaches existing traces/export, and does not trigger successful rule
     * state.
     */
    @Test
    fun coordinateFailureIsDiagnosedAndTheNextValidEvaluationCanRun() = runTest {
        val rule = rule({ 10_000 }, "position:{x:'100/width',y:0}")
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val work = MatchWork(1, MatchTimingPolicy(), diagnostics) { true }
        var width = 0
        var injected = 0
        val nodes =
            object : RuleQueryNodes<Int> {
                override suspend fun root() = 1

                override fun appId(node: Int) = "app.test"

                override suspend fun refresh(node: Int) = node

                override suspend fun match(rule: ResolvedRule, node: Int) = node

                override suspend fun perform(rule: ResolvedRule, node: Int): ActionResult =
                    try {
                        val point =
                            rule.parameters.position!!.calc(
                                RuleBounds(0, 0, width, 10),
                                ScreenSize(100, 200),
                                captureInputs = diagnostics.enabled,
                            )
                        injected++
                        ActionResult("click", true, position = point)
                    } catch (error: PositionCalculationException) {
                        ActionResult("click", false, coordinateError = error.error)
                    }
            }
        val request = MatchDebugRunRequest("test", "", "", true)
        val failed = evaluateDebugRule(work, request, rule, "app.test", null, nodes)
        assertEquals("coordinate_calculation_failed", failed.reason)
        assertEquals(0, injected)
        assertEquals(RuleStatus.Ready, rule.status)
        val event =
            diagnostics.read(session.id).events.single {
                it.reason == "coordinate_calculation_failed"
            }
        assertEquals(failed.action, event.action)
        assertEquals("position.x", event.action!!.coordinateError!!.field)
        assertEquals(mapOf("width" to 0.0), event.action.coordinateError.inputs)
        val capture = diagnostics.export(session.id)
        val encoded = Json.encodeToString(MatchDiagnosticCapture.serializer(), capture)
        val restored = Json.decodeFromString(MatchDiagnosticCapture.serializer(), encoded)
        assertEquals(event, restored.events.single { it.reason == "coordinate_calculation_failed" })

        // The automatic query path must not commit a failed action, but can commit the next valid
        // one.
        var commits = 0
        suspend fun automaticQuery() =
            queryRules(
                work,
                ForegroundRuleSnapshot(
                    TopActivity("app.test", null),
                    false,
                    RuleSummary(appIdToRules = mapOf("app.test" to listOf(rule))),
                ),
                nodes,
                null,
                false,
                { true },
                { _, _, _ -> },
                {},
                completed = { _, _, _ -> commits++ },
            )
        automaticQuery()
        assertEquals(0, commits)
        assertEquals(0, injected)
        width = 20
        automaticQuery()
        assertEquals(1, commits)
        assertEquals(1, injected)
    }

    private fun rule(now: () -> Long, extra: String = ""): AppRule {
        val raw =
            RawSubscriptionParser.parse(
                """{id:1,name:'Test',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[{matches:'*',$extra}]}]}]}""",
            )
        return RuleSummaryBuilder.build(
                RuleRuntime(now = now),
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), raw)),
                mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
            .appIdToRules
            .getValue("app.test")
            .single()
            .also { it.resetState(now()) }
    }

    /** A match may disappear during actionDelay; the old target must never be clicked. */
    @Test
    fun singleRunReadsAndMatchesAgainAfterActionDelay() = runTest {
        val rule = rule({ 10_000 + testScheduler.currentTime }, "actionDelay:200")
        val work = MatchWork(1, MatchTimingPolicy(), MatchDiagnostics()) { true }
        var reads = 0
        var actions = 0
        val nodes =
            object : RuleQueryNodes<Int> {
                override suspend fun root(): Int = ++reads

                override fun appId(node: Int) = "app.test"

                override suspend fun refresh(node: Int) = node

                override suspend fun match(rule: ResolvedRule, node: Int): Int? = node.takeIf {
                    it == 1
                }

                override suspend fun perform(rule: ResolvedRule, node: Int): ActionResult {
                    actions++
                    return ActionResult("click", true)
                }
            }
        val result = async {
            evaluateDebugRule(
                work,
                MatchDebugRunRequest("test", "", "", true),
                rule,
                "app.test",
                null,
                nodes,
            )
        }
        runCurrent()
        assertEquals(1, reads)
        advanceTimeBy(200)
        runCurrent()
        assertEquals("not_matched", result.await().reason)
        assertEquals(2, reads)
        assertEquals(0, actions)
    }

    /** Observing a rule uses identical matching decisions and does not advance its counters. */
    @Test
    fun tracingDoesNotChangeMatchOnlyResultOrTriggerRule() = runTest {
        val rule = rule({ 10_000 }, "actionMaximum:1")
        var actions = 0
        val nodes =
            object : RuleQueryNodes<Int> {
                override suspend fun root() = 1

                override fun appId(node: Int) = "app.test"

                override suspend fun refresh(node: Int) = node

                override suspend fun match(rule: ResolvedRule, node: Int) = node

                override suspend fun perform(rule: ResolvedRule, node: Int): ActionResult {
                    actions++
                    return ActionResult("click", true)
                }
            }
        val diagnostics = MatchDiagnostics()
        val work = MatchWork(1, MatchTimingPolicy(), diagnostics) { true }
        val request = MatchDebugRunRequest("test", "", "")
        val before = evaluateDebugRule(work, request, rule, "app.test", null, nodes)
        diagnostics.start(MatchTimingPolicy())
        val after = evaluateDebugRule(work, request, rule, "app.test", null, nodes)
        assertEquals(before, after)
        assertEquals(RuleStatus.Ready, rule.status)
        assertEquals(0, actions)
    }
}
