package li.gkd.app.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleStatus
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

class RuleReconciliationTest {
    @Test
    fun foregroundTransitionFinishesStartedGestureDespiteNewPageRules() = runTest {
        for (reason in
            listOf(
                MatchInvalidationReason.PageChanged,
                MatchInvalidationReason.ForegroundConflict,
            )) {
            val coordinator =
                MatchCoordinator(
                    this,
                    StandardTestDispatcher(testScheduler),
                    onFailure = { throw it },
                )
            val service = Any()
            val finish = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val old = summary(RuleRuntime(), "{key:1,matches:'*'}")
            val rule = old.rules().first()
            var oldRun = 0L
            try {
                coordinator.attach(service)
                val activity = old.activity()
                coordinator.updateRules(activity)
                coordinator.request(service) {
                    oldRun = id
                    trackRules(activity)
                    selectRule(rule)
                    performAction(rule) {
                        events += "started"
                        finish.await()
                        events += "finished"
                        ActionResult("swipe", true)
                    }
                    assertFalse(coordinator.canCommit(id, rule))
                    performAction(rule) {
                        events += "obsolete_action"
                        ActionResult("click", true)
                    }
                }
                runCurrent()
                coordinator.request(service) { events += "obsolete_pending" }
                coordinator.scheduleRule(service, rule, RuleDelayKind.Action, 100) {
                    events += "obsolete_delay"
                }
                coordinator.invalidate(reason)
                assertFalse(coordinator.canCommit(oldRun, rule))
                coordinator.updateRules(summary(RuleRuntime(), "{key:2,matches:'*'}").activity())
                coordinator.request(service) { events += "new_page" }
                runCurrent()
                assertFalse(coordinator.snapshot().cancelling)
                assertEquals(listOf("started"), events)
                finish.complete(Unit)
                advanceUntilIdle()
                assertEquals(listOf("started", "finished", "new_page"), events)
            } finally {
                coordinator.close()
            }
        }
    }

    @Test
    fun scopedDuplicateKeysOnlyDependOnTheActuallyBoundSharedRule() {
        val runtime = RuleRuntime()
        fun build(lastSelector: String): RuleSummary {
            val sub =
                RawSubscriptionParser.parse(
                    """{id:1,name:'Test',version:1,apps:[{id:'app.test',groups:[
                {key:1,name:'Main',scopeKeys:[2,3],rules:[{key:10,matches:'*',actionCdKey:1}]},
                {key:2,name:'Bound',rules:[{key:1,matches:'*'}]},
                {key:3,name:'Other',rules:[{key:1,matches:'$lastSelector'}]}
            ]}]}""",
                )
            return RuleSummaryBuilder.build(
                runtime,
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
                mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
        val old = build("*")
        val fresh = build("[text=\"changed\"]")
        fresh.retainUnaffectedExecution(old)
        assertTrue(
            fresh.rules().first { it.key == 10 }.sameExecution(old.rules().first { it.key == 10 }),
        )
    }

    @Test
    fun removedSelectedRuleRejectsPhysicalCompletion() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val runtime = RuleRuntime()
        val service = Any()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'}")
        var cancelled = false
        try {
            coordinator.attach(service)
            val activity = old.activity()
            coordinator.updateRules(activity)
            coordinator.request(service) {
                trackRules(activity)
                selectRule(old.rules()[0])
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                    assertFalse(coordinator.canCommit(id, old.rules()[0]))
                }
            }
            runCurrent()
            val fresh = summary(runtime, "{key:2,matches:'*'}")
            fresh.retainUnaffectedExecution(old)
            assertTrue(fresh.rules()[0].sameExecution(old.rules()[1]))
            coordinator.updateRules(fresh.activity())
            runCurrent()
            assertTrue(cancelled)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun retainedDeadlinesMigrateOnceAndChangedRuleDeadlinesAreRemoved() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
        val runtime = RuleRuntime()
        val service = Any()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'}")
        val ran = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.updateRules(old.activity())
            coordinator.scheduleRule(service, old.rules()[0], RuleDelayKind.Match, 100) {
                ran += "retained"
            }
            coordinator.scheduleRule(service, old.rules()[1], RuleDelayKind.Action, 200) {
                ran += "obsolete"
            }
            val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'[text=\"new\"]'}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            coordinator.scheduleRule(service, fresh.rules()[0], RuleDelayKind.Match, 200) {
                ran += "duplicate"
            }
            advanceUntilIdle()
            assertEquals(listOf("retained"), ran)
        } finally {
            coordinator.close()
        }
    }

    private fun summary(
        runtime: RuleRuntime,
        rules: String,
        version: Int = 1,
        groupProps: String = "",
    ) =
        RuleSummaryBuilder.build(
            runtime,
            listOf(
                UsedSubsEntry(
                    SubsItem(1, enable = true, order = 0),
                    RawSubscriptionParser.parse(
                        """{id:1,name:'Test',version:$version,apps:[{id:'app.test',groups:[{key:1,name:'Test',$groupProps rules:[$rules]}]}]}""",
                    ),
                ),
            ),
            mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
            emptyList(),
            emptyList(),
            emptyList(),
        )

    private fun RuleSummary.rules() = appIdToRules.getValue("app.test")

    private fun RuleSummary.activity() =
        ForegroundRuleSnapshot(TopActivity("app.test"), false, this)

    @Test
    fun unrelatedEditAndVersionBumpPreserveCountCooldownAndPrerequisite() {
        val runtime = RuleRuntime(now = { 10000 })
        val old =
            summary(
                runtime,
                "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'*',preKeys:[1]},{key:3,matches:'*'}",
            )
        old.rules()[0].trigger()
        val fresh =
            summary(
                runtime,
                "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'*',preKeys:[1]},{key:3,matches:'[text=\"new\"]'}",
                2,
            )
        fresh.retainUnaffectedExecution(old)
        assertTrue(fresh.rules()[0].sameExecution(old.rules()[0]))
        assertEquals(RuleStatus.ActionLimitReached, fresh.rules()[0].status)
        assertEquals(RuleStatus.Ready, fresh.rules()[1].status)
        assertFalse(fresh.rules()[2].sameExecution(old.rules()[2]))
    }

    @Test
    fun editsPropagateThroughSharedCountCooldownAndTransitivePrerequisites() {
        val runtime = RuleRuntime()
        val suffix =
            ",{key:2,matches:'*',actionMaximumKey:1},{key:3,matches:'*',actionCdKey:2},{key:4,matches:'*',preKeys:[3]},{key:5,matches:'*'}"
        val old = summary(runtime, "{key:1,matches:'*'}$suffix")
        val fresh = summary(runtime, "{key:1,matches:'[text=\"changed\"]'}$suffix")
        fresh.retainUnaffectedExecution(old)
        repeat(4) { assertFalse(fresh.rules()[it].sameExecution(old.rules()[it])) }
        assertTrue(fresh.rules()[4].sameExecution(old.rules()[4]))
    }

    @Test
    fun removingASharingEdgeInvalidatesItsOldPeer() {
        val runtime = RuleRuntime()
        val old =
            summary(
                runtime,
                "{key:1,matches:'*'},{key:2,matches:'*',actionCdKey:1},{key:3,matches:'*'}",
            )
        val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'},{key:3,matches:'*'}")
        fresh.retainUnaffectedExecution(old)
        assertFalse(fresh.rules()[0].sameExecution(old.rules()[0]))
        assertFalse(fresh.rules()[1].sameExecution(old.rules()[1]))
        assertTrue(fresh.rules()[2].sameExecution(old.rules()[2]))
    }

    @Test
    fun equivalentResetDefaultsRetainExecutionAndActivityResetBehavior() {
        val runtime = RuleRuntime(now = { 10000 })
        val base = "key:1,matches:'*',actionMaximum:1"
        for ((ruleProps, groupProps) in
            listOf(
                "resetMatch:'activity'" to "",
                "" to "resetMatch:'activity',",
                "resetMatch:'unknown'" to "resetMatch:'app',",
            )) {
            val old = summary(runtime, "{$base}")
            old.rules().single().trigger()
            val fresh = summary(runtime, "{$base,$ruleProps}", groupProps = groupProps)
            fresh.retainUnaffectedExecution(old)
            val rule = fresh.rules().single()
            assertTrue(rule.sameExecution(old.rules().single()))
            assertEquals(RuleStatus.ActionLimitReached, rule.status)
            rule.onActivityTransition(10000, previouslyMatched = true)
            assertEquals(RuleStatus.Ready, rule.status)
        }
    }

    @Test
    fun resetModeInheritanceAndOverrideControlExecutionAndReconciliation() {
        val runtime = RuleRuntime(now = { 10000 })
        val base = "key:1,matches:'*',actionMaximum:1"
        val inherited = summary(runtime, "{$base}", groupProps = "resetMatch:'app',")
        val inheritedRule = inherited.rules().single()
        inheritedRule.trigger()
        inheritedRule.onActivityTransition(10000, previouslyMatched = true)
        assertEquals(RuleStatus.ActionLimitReached, inheritedRule.status)

        val explicit =
            summary(runtime, "{$base,resetMatch:'app'}", groupProps = "resetMatch:'match',")
        explicit.retainUnaffectedExecution(inherited)
        assertTrue(explicit.rules().single().sameExecution(inheritedRule))

        val changed =
            summary(runtime, "{$base,resetMatch:'match'}", groupProps = "resetMatch:'app',")
        changed.retainUnaffectedExecution(explicit)
        val changedRule = changed.rules().single()
        assertFalse(changedRule.sameExecution(explicit.rules().single()))
        changedRule.trigger()
        changedRule.onActivityTransition(10000, previouslyMatched = true)
        assertEquals(RuleStatus.ActionLimitReached, changedRule.status)
        changedRule.onActivityTransition(10000, previouslyMatched = false)
        assertEquals(RuleStatus.Ready, changedRule.status)
    }

    @Test
    fun metadataAndOverriddenDefaultsRetainExecutionButEffectiveDefaultsResetIt() {
        val runtime = RuleRuntime(now = { 10000 })
        val rules =
            "{key:1,name:'Old',matches:'*',actionMaximum:1,actionCd:200},{key:2,matches:'*',actionMaximum:1}"
        val old = summary(runtime, rules, groupProps = "actionCd:100,")
        old.rules().forEach { it.trigger() }
        val fresh =
            summary(
                runtime,
                rules.replace("name:'Old'", "name:'New',exampleUrls:['https://example.com']"),
                version = 2,
                groupProps = "actionCd:300,",
            )
        fresh.retainUnaffectedExecution(old)
        assertTrue(fresh.rules()[0].sameExecution(old.rules()[0]))
        assertEquals(RuleStatus.ActionLimitReached, fresh.rules()[0].status)
        assertFalse(fresh.rules()[1].sameExecution(old.rules()[1]))
        assertEquals(RuleStatus.Ready, fresh.rules()[1].status)
    }

    @Test
    fun effectiveActionSelectorAndTimingEditsResetExecution() {
        val runtime = RuleRuntime(now = { 10000 })
        val before =
            "key:1,matches:'*',actionMaximum:1,action:'click',position:{x:10,y:20},actionCd:100"
        val changes =
            listOf(
                "action:'click'" to "action:'longClick'",
                "x:10" to "x:30",
                "position:{x:10,y:20}" to
                    "swipeArg:{start:{x:10,y:20},end:{x:30,y:40},duration:500}",
                "matches:'*'" to "matches:'[text=\"changed\"]'",
                "actionCd:100" to "actionCd:200",
                "actionCd:100" to "actionCd:100,actionDelay:50",
            )
        for ((from, to) in changes) {
            val old = summary(runtime, "{$before}")
            old.rules().single().trigger()
            val fresh = summary(runtime, "{${before.replace(from, to)}}")
            fresh.retainUnaffectedExecution(old)
            assertFalse(fresh.rules().single().sameExecution(old.rules().single()), to)
            assertEquals(RuleStatus.Ready, fresh.rules().single().status, to)
        }
    }

    @Test
    fun switchingSharedStateSourceResetsExecutionEvenWithIdenticalDependencySets() {
        val runtime = RuleRuntime()
        for (field in listOf("actionCdKey", "actionMaximumKey")) {
            val prefix = "{key:1,matches:'*'},{key:2,matches:'*'},"
            val old = summary(runtime, prefix + "{key:3,matches:'*',preKeys:[1,2],$field:1}")
            val fresh = summary(runtime, prefix + "{key:3,matches:'*',preKeys:[1,2],$field:2}")
            assertEquals(
                old.rules()[2].executionDependencies().map { it.key }.toSet(),
                fresh.rules()[2].executionDependencies().map { it.key }.toSet(),
            )
            fresh.retainUnaffectedExecution(old)
            repeat(3) {
                assertFalse(
                    fresh.rules()[it].sameExecution(old.rules()[it]),
                    "$field rule ${it + 1}",
                )
            }
        }
    }

    @Test
    fun unrelatedLowerRuleUpdateKeepsInputAndCommitsIntoNewSnapshot() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val runtime = RuleRuntime(now = { 10000 })
        val service = Any()
        val finish = CompletableDeferred<Unit>()
        val old = summary(runtime, "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'*'}")
        var committed = false
        try {
            coordinator.attach(service)
            val activity = old.activity()
            coordinator.updateRules(activity)
            coordinator.request(service) {
                trackRules(activity)
                selectRule(old.rules()[0])
                finish.await()
                if (coordinator.canCommit(id, old.rules()[0])) {
                    old.rules()[0].trigger()
                    committed = true
                }
            }
            runCurrent()
            val fresh =
                summary(
                    runtime,
                    "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'[text=\"new\"]'}",
                )
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            finish.complete(Unit)
            runCurrent()
            assertTrue(committed)
            assertEquals(RuleStatus.ActionLimitReached, fresh.rules()[0].status)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun newHigherPriorityRuleCancelsAndRejectsLateCommitUntilCleanup() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val runtime = RuleRuntime()
        val service = Any()
        val cleanup = CompletableDeferred<Unit>()
        val old = summary(runtime, "{key:1,matches:'*'}")
        var lateCommit = true
        var nextStarted = false
        try {
            coordinator.attach(service)
            val activity = old.activity()
            coordinator.updateRules(activity)
            coordinator.request(service) {
                trackRules(activity)
                selectRule(old.rules()[0])
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { cleanup.await() }
                    lateCommit = coordinator.canCommit(id, old.rules()[0])
                }
            }
            runCurrent()
            val fresh =
                summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*',priorityTime:5000}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            coordinator.request(service) { nextStarted = true }
            runCurrent()
            assertFalse(nextStarted)
            cleanup.complete(Unit)
            runCurrent()
            assertFalse(lateCommit)
            assertTrue(nextStarted)
        } finally {
            cleanup.complete(Unit)
            coordinator.close()
        }
    }
}
