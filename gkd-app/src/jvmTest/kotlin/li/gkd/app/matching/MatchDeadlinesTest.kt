package li.gkd.app.matching

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchDeadlinesTest {
    @Test
    fun taskOwnershipSurvivesQueryCommandsAndReleasesRejectedReplacedAndCancelledTasks() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
        // Inspect retained closures without adding a production-only testing API.
        val tasks =
            MatchCoordinator::class
                .java
                .getDeclaredField("tasks")
                .apply { isAccessible = true }
                .get(coordinator) as Map<*, *>
        val service = Any()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val rule = rule(RuleRuntime())
        val runs = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.request(service) {
                match(rule) {
                    assertEquals(1, tasks.size)
                    "matched"
                }
                assertEquals(1, tasks.size)
                release.await()
                runs += "active"
            }
            runCurrent()
            coordinator.request(Any()) { error("Rejected task ran") }
            assertEquals(1, tasks.size)
            coordinator.request(service) { error("Replaced task ran") }
            coordinator.request(service) { runs += "pending" }
            assertEquals(2, tasks.size)
            coordinator.scheduleFollowUp(service) { error("Replaced follow-up ran") }
            coordinator.scheduleFollowUp(service) { runs += "follow-up" }
            coordinator.scheduleRule(service, rule, RuleDelayKind.Action, 100) { runs += "rule" }
            coordinator.scheduleRule(service, rule, RuleDelayKind.Action, 100) {
                error("Duplicate ran")
            }
            assertEquals(4, tasks.size)
            coordinator.cancelRuleDelays(rule)
            assertEquals(3, tasks.size)
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf("active", "pending"), runs)
            assertEquals(1, tasks.size)
            coordinator.invalidate(MatchInvalidationReason.PageChanged)
            assertEquals(0, tasks.size)
            advanceUntilIdle()
            assertEquals(listOf("active", "pending"), runs)
        } finally {
            coordinator.close()
        }
        assertEquals(0, tasks.size)
    }

    @Test
    fun policyUpdateRebasesFollowUpButPreservesRuleDeadlines() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
        val service = Any()
        val runs = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.scheduleFollowUp(service) { runs += "poll" }
            coordinator.scheduleRule(service, rule(RuleRuntime()), RuleDelayKind.Action, 250) {
                runs += "rule"
            }
            advanceTimeBy(100)
            coordinator.updatePolicy(coordinator.policy.value.copy(followUpMillis = 150))
            assertEquals(50L, coordinator.snapshot().nextWakeMillis)
            advanceTimeBy(50)
            runCurrent()
            assertEquals(listOf("poll"), runs)
            advanceTimeBy(100)
            runCurrent()
            assertEquals(listOf("poll", "rule"), runs)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun lengtheningFollowUpCancelsOldTimerAndOverdueUpdateRunsImmediately() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
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
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun activeWorkKeepsItsPolicyAndNextWorkUsesTheUpdate() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                onFailure = { throw it },
            )
        val service = Any()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val values = mutableListOf<Long>()
        try {
            coordinator.attach(service)
            coordinator.request(service) {
                release.await()
                values += policy.rootWaitMillis
            }
            runCurrent()
            coordinator.updatePolicy(coordinator.policy.value.copy(rootWaitMillis = 200))
            coordinator.request(service) { values += policy.rootWaitMillis }
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(500L, 200L), values)
        } finally {
            coordinator.close()
        }
    }

    private fun rule(runtime: RuleRuntime): ResolvedRule {
        val sub =
            RawSubscriptionParser.parse(
                """{id:1,name:'Test',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[{matches:'*'}]}]}]}""",
            )
        return RuleSummaryBuilder.build(
                runtime,
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
                mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
            .appIdToRules
            .getValue("app.test")
            .single()
    }

    @Test
    fun ruleResetCancelsBothDelaysAndAllowsFreshScheduling() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
        val service = Any()
        coordinator.attach(service)
        val rule = rule(RuleRuntime(cancelPending = coordinator::cancelRuleDelays))
        var resumed = 0
        try {
            for (kind in RuleDelayKind.entries) coordinator.scheduleRule(service, rule, kind, 100) {
                resumed++
            }
            advanceTimeBy(50)
            rule.resetState(50)
            coordinator.scheduleRule(service, rule, RuleDelayKind.Match, 100) { resumed++ }
            advanceTimeBy(50)
            runCurrent()
            assertEquals(0, resumed)
            advanceTimeBy(50)
            runCurrent()
            assertEquals(1, resumed)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun duplicateDeadlineRunsOnceAndCompletionAllowsRescheduling() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
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
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun serviceHandoffDiscardsOldDeadlinesAndOneWakeCoalescesDueRules() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
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
        } finally {
            coordinator.close()
        }
    }
}
