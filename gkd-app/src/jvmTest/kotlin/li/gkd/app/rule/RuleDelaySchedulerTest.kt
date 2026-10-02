package li.gkd.app.rule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleDelaySchedulerTest {
    private fun rule(runtime: RuleRuntime): ResolvedRule {
        val sub = RawSubscription.parse(
            """{id:1,name:'Test',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[{matches:'*'}]}]}]}"""
        )
        return RuleSummaryBuilder.build(
            RuleGroupPolicy(), runtime,
            listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
            mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
            emptyList(), emptyList(), emptyList(),
        ).appIdToRules.getValue("app.test").single()
    }

    @Test
    fun ruleResetCancelsBothDelaysAndAllowsFreshScheduling() = runTest {
        val scheduler = RuleDelayScheduler()
        val rule = rule(RuleRuntime(cancelPending = scheduler::cancel))
        val dispatcher = StandardTestDispatcher(testScheduler)
        var resumed = 0
        for (kind in RuleDelayScheduler.Kind.entries) {
            scheduler.schedule(rule, kind, this, dispatcher, 100) { resumed++ }
        }
        runCurrent()
        advanceTimeBy(50)
        rule.resetState(50)
        scheduler.schedule(rule, RuleDelayScheduler.Kind.Match, this, dispatcher, 100) { resumed++ }
        advanceTimeBy(50)
        runCurrent()
        assertEquals(0, resumed)
        advanceTimeBy(50)
        runCurrent()
        assertEquals(1, resumed)
    }

    @Test
    fun duplicateScheduleRunsOnceAndCompletionAllowsRescheduling() = runTest {
        val scheduler = RuleDelayScheduler()
        val rule = rule(RuleRuntime())
        val dispatcher = StandardTestDispatcher(testScheduler)
        var resumed = 0
        repeat(2) {
            scheduler.schedule(rule, RuleDelayScheduler.Kind.Action, this, dispatcher, 100) {
                resumed++
                scheduler.schedule(
                    rule,
                    RuleDelayScheduler.Kind.Action,
                    this,
                    dispatcher,
                    100
                ) { resumed++ }
            }
        }
        advanceUntilIdle()
        assertEquals(2, resumed)
    }

    @Test
    fun serviceScopeCancellationDropsPendingWorkWithoutBlockingNewScope() = runTest {
        val scheduler = RuleDelayScheduler()
        val rule = rule(RuleRuntime())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val service = CoroutineScope(SupervisorJob() + dispatcher)
        var resumed = 0
        try {
            scheduler.schedule(
                rule,
                RuleDelayScheduler.Kind.Action,
                service,
                dispatcher,
                100
            ) { resumed++ }
            runCurrent()
            service.cancel()
            runCurrent()
            advanceUntilIdle()
            assertEquals(0, resumed)
            scheduler.schedule(
                rule,
                RuleDelayScheduler.Kind.Action,
                this,
                dispatcher,
                100
            ) { resumed++ }
            advanceUntilIdle()
            assertEquals(1, resumed)
        } finally {
            service.cancel()
        }
    }
}
