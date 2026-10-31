package li.gkd.app.matching.scenarios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.development.matching.SimulatedNodeAdapter
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.MatchExecutionState
import li.gkd.app.matching.MatchInvalidationReason
import li.gkd.app.matching.diagnostics.MatchSimulatedNode
import li.gkd.app.matching.input.ForegroundIdentity
import li.gkd.app.matching.input.ForegroundSource
import li.gkd.app.matching.input.ForegroundTracker
import li.gkd.app.matching.input.ForegroundWindowTracker
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.matching.query.RuleQueryNodes
import li.gkd.app.matching.query.matchRuleSelectors
import li.gkd.app.matching.query.queryRules
import li.gkd.app.matching.query.querySingleSelector
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

@Serializable
private data class ShareScenario(
    val title: String,
    val description: String,
    val diagnosticUrl: String,
    val engineVersion: String,
    val appId: String,
    val initialActivity: String,
    val activity: String,
    val initialWindow: Int,
    val window: Int,
    val taskAt: Long,
    val windowAt: Long,
    val rootAt: Long,
    val candidateAt: Long,
    val repeatAt: Long,
    val completedAt: Long,
    val root: MatchSimulatedNode,
    val cases: List<ScenarioCase>,
    val initialAppId: String = appId,
    val rootWindow: Int = window,
)

@Serializable
private data class ScenarioCase(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val inputChange: ScenarioInputChange,
    val expected: ScenarioExpectation,
)

@Serializable
private enum class InputChangeType {
    None,
    NewRoot,
    RepeatWindow,
    NewWindow,
    NewActivity,
}

@Serializable
private data class ScenarioInputChange(
    val type: InputChangeType,
    val description: String,
    val windowId: Int? = null,
    val activityId: String? = null,
)

@Serializable
private data class ScenarioExpectation(
    val description: String,
    val matched: Boolean,
    val actions: List<String>,
    val commits: Int,
)

class RepeatedWindowEventScenarioTest {
    @Test fun WE001_duplicateWindowEventStillClicksSixSecondSkip() = replay("WE-001")

    @Test fun WE002_newWindowNeverClicksOldSkip() = replay("WE-002")

    @Test fun WE003_newActivityNeverClicksOldSkip() = replay("WE-003")

    @Test
    fun WI001_contentOnlySplashStillClicks() = replay("WI-001", "content-only-splash-window.json")

    @Test
    fun WI002_replacedContentOnlyRootNeverClicksOldSkip() =
        replay("WI-002", "content-only-splash-window.json")

    private fun replay(caseId: String, file: String = "repeated-window-event.json") = runTest {
        val fixture = checkNotNull(javaClass.getResource("/matching-scenarios/$file"))
        val sample = Json.decodeFromString<ShareScenario>(fixture.readText())
        val case = sample.cases.single { it.id == caseId }
        val change = case.inputChange
        val expected = case.expected
        val label =
            "${case.id} ${case.title}: ${case.description}; " +
                "输入：${change.description}; 预期：${expected.description}; ${sample.diagnosticUrl}"
        val main = ForegroundIdentity(sample.appId, sample.activity)
        val initial = ForegroundIdentity(sample.initialAppId, sample.initialActivity)
        val foreground = ForegroundTracker(initial)
        val windows = ForegroundWindowTracker()
        val policy = MatchTimingPolicy()
        val service = Any()
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
        var state = MatchExecutionState(Any(), true, true, false, true, true)
        val actions = mutableListOf<String>()
        var commits = 0
        var matched = false
        fun observeWindow(id: Int, identity: ForegroundIdentity) {
            if (windows.observeState(id, identity)) {
                state = state.copy(page = Any())
                coordinator.invalidate(MatchInvalidationReason.ForegroundConflict)
            }
            foreground.observe(
                ForegroundSource.Accessibility,
                identity,
                policy,
                testScheduler.currentTime,
            )
        }
        fun advanceTo(time: Long) {
            advanceTimeBy(time - testScheduler.currentTime)
            runCurrent()
        }
        try {
            coordinator.attach(service)
            windows.observeState(sample.initialWindow, initial)
            advanceTo(sample.taskAt)
            foreground.observe(ForegroundSource.TaskStack, main, policy, testScheduler.currentTime)
            advanceTo(sample.windowAt)
            observeWindow(sample.window, main)
            advanceTo(sample.rootAt)
            windows.observeRoot(sample.rootWindow, sample.appId, initial)
            val confirmed =
                foreground.observe(
                    ForegroundSource.Nodes,
                    checkNotNull(windows.identity),
                    policy,
                    testScheduler.currentTime,
                )
            assertEquals(main, confirmed.confirmed, label)
            assertEquals(null, confirmed.pending, label)
            val raw =
                RawSubscriptionParser.parse(
                    """{id:1,name:'Regression',version:1,apps:[{id:'${sample.appId}',groups:[
                  {key:0,name:'Splash',rules:[{matches:'[text*="跳过"][visibleToUser=true]',
                  excludeMatches:'[text="blocked"]',action:'clickCenter',actionMaximum:1}]}]}]}""",
                )
            val summary =
                RuleSummaryBuilder.build(
                    RuleRuntime(now = { 10_000 + testScheduler.currentTime }),
                    listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), raw)),
                    mapOf(
                        sample.appId to
                            AppInfo(sample.appId, "Fixture", 1, "1", false, 0, false, 0),
                    ),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            val rule = summary.appIdToRules.getValue(sample.appId).single()
            rule.resetState(10_000 + testScheduler.currentTime)
            val activity =
                ForegroundRuleSnapshot(TopActivity(sample.appId, sample.activity), false, summary)
            coordinator.updateRules(activity)
            val page = state.page
            val root = sample.root
            advanceTo(sample.candidateAt)
            coordinator.request(service) {
                val adapter =
                    SimulatedNodeAdapter(root, checkNotNull(currentCoroutineContext()[Job]))
                val nodes =
                    object : RuleQueryNodes<MatchSimulatedNode> {
                        override suspend fun root() = root

                        override fun appId(node: MatchSimulatedNode) = sample.appId

                        override suspend fun refresh(node: MatchSimulatedNode) = node

                        override suspend fun match(
                            rule: ResolvedRule,
                            node: MatchSimulatedNode,
                        ): MatchSimulatedNode? {
                            val target =
                                matchRuleSelectors(
                                    rule,
                                    node,
                                    { root },
                                    { start, selector ->
                                        querySingleSelector(
                                            start,
                                            selector,
                                            { root },
                                            adapter,
                                            rule.matchOptions,
                                            true,
                                        )
                                    },
                                )
                            matched = target != null
                            // Hold completion across the event, representing asynchronous node
                            // reads.
                            delay(sample.completedAt - sample.candidateAt)
                            return target
                        }

                        override suspend fun perform(
                            rule: ResolvedRule,
                            node: MatchSimulatedNode,
                        ): ActionResult {
                            actions += node.attributes.getValue("text").content
                            return ActionResult("clickCenter", true)
                        }
                    }
                queryRules(
                    this,
                    activity,
                    nodes,
                    null,
                    false,
                    { state.permits(page) },
                    { _, _, _ -> error("Unexpected rule delay: $label") },
                    { error("Unexpected app correction: $label") },
                    { selected, _, _ ->
                        if (
                            state.page === page &&
                                !state.foregroundPending &&
                                coordinator.canCommit(id, selected)
                        ) {
                            selected.trigger()
                            commits++
                        }
                    },
                )
            }
            runCurrent()
            assertEquals(expected.matched, matched, label)
            assertTrue(actions.isEmpty(), label)
            advanceTo(sample.repeatAt)
            when (change.type) {
                InputChangeType.None -> Unit
                InputChangeType.NewRoot -> {
                    if (windows.observeRoot(requireNotNull(change.windowId), sample.appId, main)) {
                        state = state.copy(page = Any())
                        coordinator.invalidate(MatchInvalidationReason.ForegroundConflict)
                    }
                }
                InputChangeType.NewWindow -> observeWindow(requireNotNull(change.windowId), main)
                InputChangeType.NewActivity ->
                    observeWindow(
                        sample.window,
                        main.copy(activityId = requireNotNull(change.activityId)),
                    )
                InputChangeType.RepeatWindow -> observeWindow(sample.window, main)
            }
            advanceTo(sample.completedAt)
            assertEquals(
                expected.actions,
                actions,
                label,
            )
            assertEquals(expected.commits, commits, label)
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(expected.actions.size, actions.size, label)
            assertEquals(null, coordinator.snapshot().activeRunId, label)
        } finally {
            coordinator.close()
        }
    }
}
