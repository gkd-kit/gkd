package li.gkd.app.matching

import li.gkd.app.rule.RuleRuntime
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
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.model.AppInfo
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
        for (reason in listOf("page_changed", "foreground_conflict")) {
            val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
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
                    trackRules(activity); selectRule(rule)
                    performAction(rule) {
                        events += "started"
                        finish.await()
                        events += "finished"
                        li.gkd.app.model.ActionResult("swipe", true)
                    }
                    assertFalse(coordinator.canCommit(id, rule))
                    performAction(rule) {
                        events += "obsolete_action"
                        li.gkd.app.model.ActionResult("click", true)
                    }
                }
                runCurrent()
                coordinator.request(service) { events += "obsolete_pending" }
                coordinator.scheduleRule(service, rule, RuleDelayKind.Action, 100) { events += "obsolete_delay" }
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
            } finally { coordinator.close() }
        }
    }

    @Test
    fun scopedDuplicateKeysOnlyDependOnTheActuallyBoundSharedRule() {
        val runtime = RuleRuntime()
        fun build(lastSelector: String): RuleSummary {
            val sub = RawSubscriptionParser.parse("""{id:1,name:'Test',version:1,apps:[{id:'app.test',groups:[
                {key:1,name:'Main',scopeKeys:[2,3],rules:[{key:10,matches:'*',actionCdKey:1}]},
                {key:2,name:'Bound',rules:[{key:1,matches:'*'}]},
                {key:3,name:'Other',rules:[{key:1,matches:'$lastSelector'}]}
            ]}]}""")
            return RuleSummaryBuilder.build(runtime,
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
                mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
                emptyList(), emptyList(), emptyList())
        }
        val old = build("*")
        val fresh = build("[text=\"changed\"]")
        fresh.retainUnaffectedExecution(old)
        assertTrue(fresh.rules().first { it.key == 10 }.sameExecution(old.rules().first { it.key == 10 }))
    }

    @Test
    fun removedSelectedRuleRejectsPhysicalCompletion() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val runtime = RuleRuntime()
        val service = Any()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'}")
        var cancelled = false
        try {
            coordinator.attach(service)
            val activity = old.activity()
            coordinator.updateRules(activity)
            coordinator.request(service) {
                trackRules(activity); selectRule(old.rules()[0])
                try { awaitCancellation() } finally {
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
        } finally { coordinator.close() }
    }

    @Test
    fun retainedDeadlinesMigrateOnceAndChangedRuleDeadlinesAreRemoved() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val runtime = RuleRuntime()
        val service = Any()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'}")
        val ran = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.updateRules(old.activity())
            coordinator.scheduleRule(service, old.rules()[0], RuleDelayKind.Match, 100) { ran += "retained" }
            coordinator.scheduleRule(service, old.rules()[1], RuleDelayKind.Action, 200) { ran += "obsolete" }
            val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'[text=\"new\"]'}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            coordinator.scheduleRule(service, fresh.rules()[0], RuleDelayKind.Match, 200) { ran += "duplicate" }
            advanceUntilIdle()
            assertEquals(listOf("retained"), ran)
        } finally { coordinator.close() }
    }

    private fun summary(runtime: RuleRuntime, rules: String, version: Int = 1) = RuleSummaryBuilder.build(
        runtime, listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), RawSubscriptionParser.parse(
            """{id:1,name:'Test',version:$version,apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[$rules]}]}]}"""
        ))), mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
        emptyList(), emptyList(), emptyList(),
    )
    private fun RuleSummary.rules() = appIdToRules.getValue("app.test")
    private fun RuleSummary.activity() = ForegroundRuleSnapshot(TopActivity("app.test"), false, this)

    @Test
    fun unrelatedEditAndVersionBumpPreserveCountCooldownAndPrerequisite() {
        val runtime = RuleRuntime(now = { 10000 })
        val old = summary(runtime, "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'*',preKeys:[1]},{key:3,matches:'*'}")
        old.rules()[0].trigger()
        val fresh = summary(runtime, "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'*',preKeys:[1]},{key:3,matches:'[text=\"new\"]'}", 2)
        fresh.retainUnaffectedExecution(old)
        assertTrue(fresh.rules()[0].sameExecution(old.rules()[0]))
        assertEquals(RuleStatus.ActionLimitReached, fresh.rules()[0].status)
        assertEquals(RuleStatus.Ready, fresh.rules()[1].status)
        assertFalse(fresh.rules()[2].sameExecution(old.rules()[2]))
    }

    @Test
    fun editsPropagateThroughSharedCountCooldownAndTransitivePrerequisites() {
        val runtime = RuleRuntime()
        val suffix = ",{key:2,matches:'*',actionMaximumKey:1},{key:3,matches:'*',actionCdKey:2},{key:4,matches:'*',preKeys:[3]},{key:5,matches:'*'}"
        val old = summary(runtime, "{key:1,matches:'*'}$suffix")
        val fresh = summary(runtime, "{key:1,matches:'[text=\"changed\"]'}$suffix")
        fresh.retainUnaffectedExecution(old)
        repeat(4) { assertFalse(fresh.rules()[it].sameExecution(old.rules()[it])) }
        assertTrue(fresh.rules()[4].sameExecution(old.rules()[4]))
    }

    @Test
    fun removingASharingEdgeInvalidatesItsOldPeer() {
        val runtime = RuleRuntime()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*',actionCdKey:1},{key:3,matches:'*'}")
        val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'},{key:3,matches:'*'}")
        fresh.retainUnaffectedExecution(old)
        assertFalse(fresh.rules()[0].sameExecution(old.rules()[0]))
        assertFalse(fresh.rules()[1].sameExecution(old.rules()[1]))
        assertTrue(fresh.rules()[2].sameExecution(old.rules()[2]))
    }

    @Test
    fun unrelatedLowerRuleUpdateKeepsInputAndCommitsIntoNewSnapshot() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
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
                trackRules(activity); selectRule(old.rules()[0])
                finish.await()
                if (coordinator.canCommit(id, old.rules()[0])) { old.rules()[0].trigger(); committed = true }
            }
            runCurrent()
            val fresh = summary(runtime, "{key:1,matches:'*',actionMaximum:1},{key:2,matches:'[text=\"new\"]'}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            finish.complete(Unit)
            runCurrent()
            assertTrue(committed)
            assertEquals(RuleStatus.ActionLimitReached, fresh.rules()[0].status)
        } finally { coordinator.close() }
    }

    @Test
    fun newHigherPriorityRuleCancelsAndRejectsLateCommitUntilCleanup() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
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
                trackRules(activity); selectRule(old.rules()[0])
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { cleanup.await() }
                    lateCommit = coordinator.canCommit(id, old.rules()[0])
                }
            }
            runCurrent()
            val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*',priorityTime:5000}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            coordinator.request(service) { nextStarted = true }
            runCurrent()
            assertFalse(nextStarted)
            cleanup.complete(Unit)
            runCurrent()
            assertFalse(lateCommit)
            assertTrue(nextStarted)
        } finally { cleanup.complete(Unit); coordinator.close() }
    }
}
