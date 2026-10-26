package li.gkd.app.matching

import li.gkd.app.rule.RuleRuntime
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchDeadlinesTest {
    @Test
    fun policyUpdateRebasesFollowUpButPreservesRuleDeadlines() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val service = Any()
        val runs = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.scheduleFollowUp(service) { runs += "poll" }
            coordinator.scheduleRule(service, rule(RuleRuntime()), RuleDelayKind.Action, 250) { runs += "rule" }
            advanceTimeBy(100)
            coordinator.updatePolicy(coordinator.policy.value.copy(followUpMillis = 150))
            assertEquals(50L, coordinator.snapshot().nextWakeMillis)
            advanceTimeBy(50)
            runCurrent()
            assertEquals(listOf("poll"), runs)
            advanceTimeBy(100)
            runCurrent()
            assertEquals(listOf("poll", "rule"), runs)
        } finally { coordinator.close() }
    }

    @Test
    fun lengtheningFollowUpCancelsOldTimerAndOverdueUpdateRunsImmediately() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val service = Any()
        var runs = 0
        try {
            coordinator.attach(service)
            coordinator.scheduleFollowUp(service) { runs++ }
            advanceTimeBy(100)
            coordinator.updatePolicy(coordinator.policy.value.copy(followUpMillis = 600))
            advanceTimeBy(250)
            runCurrent()
            assertEquals(0, runs)
            coordinator.updatePolicy(coordinator.policy.value.copy(followUpMillis = 200))
            runCurrent()
            assertEquals(1, runs)
            advanceUntilIdle()
            assertEquals(1, runs)
        } finally { coordinator.close() }
    }

    @Test
    fun activeWorkKeepsItsPolicyAndNextWorkUsesTheUpdate() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            onFailure = { throw it })
        val service = Any()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val values = mutableListOf<Long>()
        try {
            coordinator.attach(service)
            coordinator.request(service) { release.await(); values += policy.rootWaitMillis }
            runCurrent()
            coordinator.updatePolicy(coordinator.policy.value.copy(rootWaitMillis = 200))
            coordinator.request(service) { values += policy.rootWaitMillis }
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(500L, 200L), values)
        } finally { coordinator.close() }
    }

    private fun rule(runtime: RuleRuntime): ResolvedRule {
        val sub = RawSubscriptionParser.parse(
            """{id:1,name:'Test',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[{matches:'*'}]}]}]}"""
        )
        return RuleSummaryBuilder.build(
            runtime,
            listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
            mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
            emptyList(), emptyList(), emptyList(),
        ).appIdToRules.getValue("app.test").single()
    }

    @Test
    fun ruleResetCancelsBothDelaysAndAllowsFreshScheduling() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val service = Any()
        coordinator.attach(service)
        val rule = rule(RuleRuntime(cancelPending = coordinator::cancelRuleDelays))
        var resumed = 0
        try {
            for (kind in RuleDelayKind.entries) coordinator.scheduleRule(service, rule, kind, 100) { resumed++ }
            advanceTimeBy(50)
            rule.resetState(50)
            coordinator.scheduleRule(service, rule, RuleDelayKind.Match, 100) { resumed++ }
            advanceTimeBy(50)
            runCurrent()
            assertEquals(0, resumed)
            advanceTimeBy(50)
            runCurrent()
            assertEquals(1, resumed)
        } finally { coordinator.close() }
    }

    @Test
    fun duplicateDeadlineRunsOnceAndCompletionAllowsRescheduling() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val service = Any()
        coordinator.attach(service)
        val rule = rule(RuleRuntime())
        var resumed = 0
        try {
            repeat(2) {
                coordinator.scheduleRule(service, rule, RuleDelayKind.Action, 100) {
                    resumed++
                    coordinator.scheduleRule(service, rule, RuleDelayKind.Action, 100) { resumed++ }
                }
            }
            advanceUntilIdle()
            assertEquals(2, resumed)
        } finally { coordinator.close() }
    }

    @Test
    fun serviceHandoffDiscardsOldDeadlinesAndOneWakeCoalescesDueRules() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val old = Any()
        val fresh = Any()
        val first = rule(RuleRuntime())
        val second = rule(RuleRuntime())
        var resumed = 0
        try {
            coordinator.attach(old)
            coordinator.scheduleRule(old, first, RuleDelayKind.Action, 100) { resumed += 10 }
            coordinator.attach(fresh)
            coordinator.scheduleRule(fresh, first, RuleDelayKind.Action, 100) { resumed++ }
            coordinator.scheduleRule(fresh, second, RuleDelayKind.Action, 100) { resumed++ }
            advanceUntilIdle()
            assertEquals(1, resumed)
        } finally { coordinator.close() }
    }
}
