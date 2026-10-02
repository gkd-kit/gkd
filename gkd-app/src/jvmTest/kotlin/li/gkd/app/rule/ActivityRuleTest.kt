package li.gkd.app.rule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

class ActivityRuleTest {
    private fun summary(runtime: RuleRuntime): RuleSummary {
        val sub = RawSubscription.parse(
            """{id:1,name:'Runtime',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Group',rules:[
                {key:1,matches:'*',order:1,activityIds:['.Allowed']},
                {key:2,matches:'*',order:2,priorityTime:100,actionCd:0}
            ]}]}],globalGroups:[{key:2,name:'Global',rules:[{matches:'*',order:3}]}]}"""
        )
        return RuleSummaryBuilder.build(
            RuleGroupPolicy(), runtime,
            listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
            mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
            emptyList(), emptyList(), emptyList(),
        )
    }

    @Test
    fun activityFilteringAndBlockingApplyToAppAndGlobalRules() {
        val summary = summary(RuleRuntime(now = { 10_000 }))
        val allowed = ActivityRule(TopActivity("app.test", "app.test.Allowed"), false, summary)
        assertEquals(listOf(1, 2, 3), allowed.currentRules.map { it.order })
        val other = ActivityRule(TopActivity("app.test", "app.test.Other"), false, summary)
        assertEquals(listOf(2, 3), other.currentRules.map { it.order })
        val blocked = ActivityRule(TopActivity("app.test", "app.test.Allowed"), true, summary)
        assertTrue(blocked.currentRules.isEmpty())
        assertTrue(blocked.skipMatch)
        assertTrue(blocked.skipConsumeEvent)
    }

    @Test
    fun priorityExpiresWithoutRecreatingActivitySnapshot() {
        var now = 10_000L
        val summary = summary(RuleRuntime(now = { now }))
        summary.appIdToRules.getValue("app.test").forEach { it.resetState(now) }
        val state = ActivityRule(TopActivity("app.test", "app.test.Allowed"), false, summary)
        assertTrue(state.activePriority)
        assertEquals(2, state.priorityRules.first().order)
        now += 100
        assertFalse(state.activePriority)
        assertEquals(listOf(1, 2, 3), state.priorityRules.map { it.order })
        assertEquals("app.tests.Main", TopActivity("app.test", "app.tests.Main").shortActivityId)
    }
}
