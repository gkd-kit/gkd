package li.gkd.app.rule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

class RuleRuntimeTest {
    @Test
    fun activityTransitionsRespectAppActivityAndMatchResetPolicies() {
        var resets = 0
        val runtime = RuleRuntime(now = { 10_000L }, cancelPending = { resets++ })
        val rules = build(
            runtime, """[{key:1,name:'Reset',rules:[
            {matches:'*',resetMatch:'app'}, {matches:'*',resetMatch:'activity'},
            {matches:'*',resetMatch:'match'}]}]"""
        ).appIdToRules.getValue("app.test")
        rules.forEach { it.resetState(10_000) }
        resets = 0
        rules.forEach { it.onActivityTransition(10_100, previouslyMatched = true) }
        assertEquals(1, resets)
        rules[2].onActivityTransition(10_200, previouslyMatched = false)
        assertEquals(2, resets)
        runtime.onAppChanged(10_300)
        rules[0].onActivityTransition(10_300, previouslyMatched = true)
        assertEquals(3, resets)
        rules[0].onActivityTransition(10_400, previouslyMatched = true)
        assertEquals(3, resets)
    }

    private fun build(runtime: RuleRuntime, groups: String, global: String = "[]"): RuleSummary {
        val sub = RawSubscription.parse(
            """{id:1,name:'Runtime',version:1,
            apps:[{id:'app.test',groups:$groups}],globalGroups:$global} """
        )
        return RuleSummaryBuilder.build(
            RuleGroupPolicy(), runtime,
            listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), sub)),
            mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
            emptyList(), emptyList(), emptyList(),
        )
    }

    @Test
    fun scopedRulesShareCooldownAndCountAndResetTheSameCounters() {
        var now = 10_000L
        val runtime = RuleRuntime(now = { now })
        val rules = build(
            runtime, """[
            {key:1,name:'Source',rules:[{key:10,matches:'*',actionCd:100,actionMaximum:2}]},
            {key:2,name:'Linked',scopeKeys:[1],rules:[{key:20,matches:'*',actionCdKey:10,actionCd:100,actionMaximumKey:10,actionMaximum:2}]}
        ]"""
        ).appIdToRules.getValue("app.test")
        val (source, linked) = rules
        source.trigger()
        assertEquals(RuleStatus.Cooldown, linked.status)
        now += 100
        assertEquals(RuleStatus.Ready, linked.status)
        linked.trigger()
        assertEquals(RuleStatus.ActionLimitReached, source.status)
        source.resetState(now)
        assertEquals(RuleStatus.Ready, linked.status)
    }

    @Test
    fun prerequisitesUseRuleIdentityAndDoNotLeakAcrossSnapshotsOrRuntimes() {
        val runtime = RuleRuntime(now = { 10_000L })
        val groups = """[{key:1,name:'Group',rules:[
            {key:1,matches:'*',actionCd:0},{key:2,matches:'*',preKeys:[1],actionCd:0}]}]"""
        val old = build(runtime, groups).appIdToRules.getValue("app.test")
        assertEquals(RuleStatus.PrerequisitePending, old[1].status)
        old[0].trigger()
        assertEquals(RuleStatus.Ready, old[1].status)
        val fresh = build(runtime, groups).appIdToRules.getValue("app.test")
        assertEquals(RuleStatus.PrerequisitePending, fresh[1].status)
        fresh[0].trigger()
        assertEquals(RuleStatus.Ready, fresh[1].status)
        assertEquals(RuleStatus.PrerequisitePending, old[1].status)
        val independent =
            build(RuleRuntime(now = { 10_000L }), groups).appIdToRules.getValue("app.test")
        assertEquals(RuleStatus.PrerequisitePending, independent[1].status)
        assertSame(fresh[0], runtime.lastTriggerRule)
        assertEquals(10_000L, runtime.lastTriggerTime)
    }

    @Test
    fun delayTimeoutForcedAndPriorityBoundariesUseInjectedTime() {
        var now = 10_000L
        var cancelled: ResolvedRule? = null
        val runtime = RuleRuntime(now = { now }, cancelPending = { cancelled = it })
        val rule = build(
            runtime, """[{key:1,name:'Timed',rules:[{matches:'*',
            matchDelay:100,matchTime:200,actionDelay:50,actionCd:100,
            forcedTime:300,priorityTime:250}]}]"""
        ).appIdToRules.getValue("app.test").single()
        runtime.onAppChanged(now)
        assertTrue(rule.isFirstMatchApp)
        rule.resetState(now)
        assertSame(rule, cancelled)
        assertFalse(rule.isFirstMatchApp)
        assertEquals(RuleStatus.MatchDelay, rule.status)
        assertFalse(rule.isPriority())
        now += 100
        assertEquals(RuleStatus.Ready, rule.status)
        assertTrue(rule.isPriority())
        assertTrue(rule.checkDelay())
        assertFalse(rule.checkDelay())
        now += 49
        assertEquals(RuleStatus.ActionDelay, rule.status)
        now++
        assertEquals(RuleStatus.Ready, rule.status)
        rule.trigger()
        assertFalse(rule.isPriority())
        now += 100
        assertEquals(RuleStatus.Ready, rule.status)
        now = 10_300
        assertEquals(RuleStatus.Ready, rule.status)
        now++
        assertEquals(RuleStatus.MatchTimeout, rule.status)
        assertTrue(rule.checkForced())
        now = 10_400
        assertFalse(rule.checkForced())
        rule.resetState(now)
        now += 100
        assertTrue(rule.checkDelay())
    }

    @Test
    fun globalRulesReadCurrentPlatformInputsWithoutAndroidRepositories() {
        var environment = RuleMatchEnvironment("launcher", setOf("system.app"))
        val runtime = RuleRuntime(environment = { environment })
        val rule = build(
            runtime,
            "[]",
            """[{key:1,name:'Global',rules:[{matches:'*'}]}]"""
        ).globalRules.single()
        assertTrue(rule.matchActivity("ordinary.app"))
        assertFalse(rule.matchActivity("launcher"))
        assertFalse(rule.matchActivity("system.app"))
        environment = RuleMatchEnvironment("other.launcher", emptySet())
        assertTrue(rule.matchActivity("launcher"))
        assertTrue(rule.matchActivity("system.app"))
        assertFalse(rule.matchActivity("other.launcher"))
    }

    @Test
    fun appActivityPrefixesStillRespectInheritedExclusions() {
        val rule = build(
            RuleRuntime(), """[{key:1,name:'App',activityIds:['.Allowed'],
            excludeActivityIds:['.AllowedBlocked'],rules:[{matches:'*'}]}]"""
        ).appIdToRules.getValue("app.test").single()
        assertTrue(rule.matchActivity("app.test", "app.test.AllowedScreen"))
        assertFalse(rule.matchActivity("app.test", "app.test.AllowedBlockedScreen"))
        assertFalse(rule.matchActivity("other.app", "app.test.AllowedScreen"))
        assertTrue(rule.matchActivity("app.test", null))
    }
}
