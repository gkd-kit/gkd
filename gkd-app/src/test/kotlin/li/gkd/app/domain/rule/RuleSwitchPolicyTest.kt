package li.gkd.app.domain.rule

import li.gkd.app.data.AppInfo
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSwitchPolicyTest {
    private val sub = RawSubscription.parse("""{
        id: -2, name: 'Test', version: 0,
        categories: [{key: 1, name: 'App', enable: true}],
        apps: [{id: 'app.id', groups: [{key: 2, name: 'App group', rules: ['[text="Ad"]']}]}],
        globalGroups: [{key: 3, name: 'Global', rules: [{matches: '[text="Ad"]'}]}]
    }""")
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)
    private val initial = SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)))

    @Test
    fun manualEnableIsRecordedEvenWhenTheGlobalDefaultAlreadyEnablesIt() {
        val group = sub.globalGroups.single()
        val target = RuleSwitchTarget.GlobalGroup(sub.id, group.key)
        val next = RuleSwitchPolicy.updateGroup(target, SubsGlobalGroupConfig(sub.id, group.key), RuleSetting.Enabled) as SubsGlobalGroupConfig
        val configs = initial.copy(globalGroupConfigs = listOf(next))
        assertEquals(RuleSetting.Enabled, RuleConfigIndex(configs).setting(target))
        assertTrue(RuleGroupPolicy.getGroupEnabled(group.copy(enable = false), next))
        assertEquals(RuleSetting.FollowDefault, RuleConfigIndex(initial).setting(target))
    }

    @Test
    fun globalAppResetOnlyRemovesThatAppsOverrideAndKeepsTheTotalSwitchAndPages() {
        val original = SubsGlobalGroupConfig(sub.id, 3, false, "!app.id\nother.app\napp.id/app.id.Page")
        val target = RuleSwitchTarget.GlobalApp(sub.id, 3, "app.id")
        val enabled = RuleSwitchPolicy.updateGroup(target, original.copy(exclude = ""), RuleSetting.Enabled)
        assertEquals(false, ExcludeData.parse(enabled.exclude).appIds["app.id"])
        val reset = RuleSwitchPolicy.updateGroup(target, original, RuleSetting.FollowDefault)
        assertEquals(false, reset.enable)
        assertEquals(mapOf("other.app" to true), ExcludeData.parse(reset.exclude).appIds)
        assertEquals(setOf("app.id" to "app.id.Page"), ExcludeData.parse(reset.exclude).activityIds)
        val totalReset = RuleSwitchPolicy.updateGroup(RuleSwitchTarget.GlobalGroup(sub.id, 3), original, RuleSetting.FollowDefault)
        assertNull(totalReset.enable)
        assertEquals(original.exclude, totalReset.exclude)
    }

    @Test
    fun appGroupResetRestoresCategoryInheritanceAndPreservesPageExclusions() {
        val original = SubsAppGroupConfig(sub.id, "app.id", 2, false, "app.id.Page")
        val reset = RuleSwitchPolicy.updateGroup(RuleSwitchTarget.AppGroup(sub.id, "app.id", 2), original, RuleSetting.FollowDefault)
        assertNull(reset.enable)
        assertEquals(original.exclude, reset.exclude)
        assertTrue(RuleGroupPolicy.getGroupEnabled(sub.apps.single().groups.single(), reset, sub.categories.single()))
        assertFalse(RuleGroupPolicy.getGroupEnabled(sub.apps.single().groups.single(), reset, sub.categories.single(),
            SubsCategoryConfig(false, sub.id, 1)))
    }

    @Test
    fun closedParentsKeepTheChildPreferenceEditableAndVisibleAsRestricted() {
        val group = sub.apps.single().groups.single()
        val configs = initial.copy(subsItems = listOf(initial.subsItems.single().copy(enable = false)),
            appConfigs = listOf(SubsAppConfig(false, sub.id, "app.id")),
            appGroupConfigs = listOf(SubsAppGroupConfig(sub.id, "app.id", 2, true)))
        val state = RuleGroupPolicy.controlState(sub, group, "app.id", configs, info, "launcher", emptySet())
        assertEquals(RuleSetting.Enabled, state.setting)
        assertTrue(state.configuredEnabled)
        assertTrue(state.canEnable)
        assertFalse(state.available)
        assertEquals(2, state.restrictions.size)
    }

    @Test
    fun globalAppPreferenceDoesNotEnableTheGlobalGroupTotalSwitch() {
        val group = sub.globalGroups.single()
        val config = SubsGlobalGroupConfig(sub.id, group.key, false, "!app.id")
        val state = RuleGroupPolicy.controlState(sub, group, "app.id", initial.copy(globalGroupConfigs = listOf(config)), info, "launcher", emptySet())
        assertTrue(state.configuredEnabled)
        assertFalse(state.available)
        assertTrue(state.restrictions.contains("Global rule group master switch is off"))
    }

    @Test
    fun requestRejectsChangedSwitchesButAcceptsConcurrentPageAndOtherAppEdits() {
        val target = RuleSwitchTarget.GlobalApp(sub.id, 3, "app.id")
        val request = RuleGroupConfigService.prepare(listOf(target), listOf(sub), initial)
        val unrelated = initial.copy(globalGroupConfigs = listOf(SubsGlobalGroupConfig(sub.id, 3, false, "other.app\napp.id/app.id.Page")))
        request.checkCurrent(RuleConfigIndex(unrelated))
        val changed = unrelated.copy(globalGroupConfigs = listOf(unrelated.globalGroupConfigs.single().copy(exclude = "app.id")))
        assertThrows(IllegalStateException::class.java) { request.checkCurrent(RuleConfigIndex(changed)) }
    }
}
