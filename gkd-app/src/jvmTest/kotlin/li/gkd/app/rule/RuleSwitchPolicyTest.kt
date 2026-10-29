package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.app.testing.TestFiles
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuleSwitchPolicyTest {

    /** Imported syntax errors may be managed; repair must respect the saved on/off preference. */
    @Test
    fun invalidAppAndGlobalGroupsKeepPreferencesButNeverEnterExecution() {
        for (definition in listOf("{matches:'['}", "{matches:'*',position:{x:'unknown',y:'0'}}")) {
            val source =
                """{id:-2,name:'Test',version:1,apps:[{id:'app.id',groups:[{key:2,name:'App',rules:[$definition]}]}],
                globalGroups:[{key:3,name:'Global',rules:[$definition]}]}"""
            val invalid = RawSubscriptionParser.parse(source)
            val repaired = RawSubscriptionParser.parse(source.replace(definition, "{matches:'*'}"))
            val global = invalid.globalGroups.single()
            val control =
                RuleGroupPolicy.controlState(
                    invalid,
                    global,
                    "app.id",
                    initial,
                    info,
                    "launcher",
                    emptySet(),
                )
            assertFalse(RuleRestriction.GlobalGroupDisabled in control.restrictions)
            val explanation =
                RuleGroupPolicy.explainControl(invalid, global, "app.id", initial, control)
            assertEquals(
                RuleControlStepKind.Restrictions,
                explanation.steps[explanation.decidingIndex].kind,
            )
            for (enabled in listOf(true, false)) {
                val appConfig = SubsAppGroupConfig(-2, "app.id", 2, enabled)
                val globalConfig = SubsGlobalGroupConfig(-2, 3, enabled)
                val configs =
                    initial.copy(
                        appGroupConfigs = listOf(appConfig),
                        globalGroupConfigs = listOf(globalConfig),
                    )
                for (subscription in listOf(invalid, repaired)) {
                    val valid = subscription === repaired
                    val app = subscription.apps.single().groups.single()
                    val global = subscription.globalGroups.single()
                    for ((group, appId) in listOf(app to "app.id", global to null)) {
                        val state =
                            RuleGroupPolicy.controlState(
                                subscription,
                                group,
                                appId,
                                configs,
                                info,
                                "launcher",
                                emptySet(),
                            )
                        assertTrue(state.canEnable)
                        assertEquals(enabled, state.configuredEnabled)
                        assertEquals(enabled && valid, state.available)
                    }
                    assertEquals(enabled && valid, RuleGroupPolicy.getGroupEnabled(app, appConfig))
                    assertEquals(
                        enabled && valid,
                        RuleGroupPolicy.getGroupEnabled(global, globalConfig),
                    )
                    val summary =
                        RuleSummaryBuilder.build(
                            RuleRuntime(),
                            listOf(UsedSubsEntry(configs.subsItems.single(), subscription)),
                            mapOf("app.id" to info),
                            configs.appConfigs,
                            configs.appGroupConfigs + configs.globalGroupConfigs,
                            configs.categoryConfigs,
                        )
                    assertEquals(if (enabled && valid) 1 else 0, summary.globalRules.size)
                    assertEquals(
                        if (enabled && valid) 1 else 0,
                        summary.appIdToRules["app.id"].orEmpty().size,
                    )
                }
            }
        }
    }

    @Test
    fun invalidVersionPatternsKeepAppAndGlobalAppPreferencesAndRepairUsesThosePreferences() {
        for (inherited in listOf(true, false)) {
            val condition = "versionName:{pattern:'['}"
            val appBody =
                if (inherited) "$condition,rules:[{matches:'*'}]"
                else "rules:[{matches:'*',$condition}]"
            val globalBody =
                if (inherited) "apps:[{id:'app.id',$condition}],rules:[{matches:'*'}]"
                else "rules:[{matches:'*',apps:[{id:'app.id',$condition}]}]"
            val source =
                """{id:-2,name:'Test',version:1,
                apps:[{id:'app.id',groups:[{key:2,name:'App',$appBody}]}],
                globalGroups:[{key:3,name:'Global',$globalBody}]}"""
            val invalid = RawSubscriptionParser.parse(source)
            val repaired =
                RawSubscriptionParser.parse(source.replace("pattern:'['", "pattern:'1[.].*'"))
            for (enabled in listOf(true, false)) {
                val setting = RuleSetting.from(enabled)
                val appConfig =
                    RuleSwitchPolicy.updateGroup(
                        RuleSwitchTarget.AppGroup(-2, "app.id", 2),
                        SubsAppGroupConfig(-2, "app.id", 2),
                        setting,
                    ) as SubsAppGroupConfig
                val globalConfig =
                    RuleSwitchPolicy.updateGroup(
                        RuleSwitchTarget.GlobalApp(-2, 3, "app.id"),
                        SubsGlobalGroupConfig(-2, 3, true),
                        setting,
                    ) as SubsGlobalGroupConfig
                val configs =
                    initial.copy(
                        appGroupConfigs = listOf(appConfig),
                        globalGroupConfigs = listOf(globalConfig),
                    )
                for (subscription in listOf(invalid, repaired)) {
                    val valid = subscription === repaired
                    val app = subscription.apps.single().groups.single()
                    val global = subscription.globalGroups.single()
                    for (group in listOf(app, global)) {
                        val state =
                            RuleGroupPolicy.controlState(
                                subscription,
                                group,
                                info.id,
                                configs,
                                info,
                                "launcher",
                                emptySet(),
                            )
                        assertTrue(state.canEnable)
                        assertEquals(setting, state.setting)
                        assertEquals(enabled, state.configuredEnabled)
                        assertEquals(enabled && valid, state.available)
                        assertFalse(RuleRestriction.VersionMismatch in state.restrictions)
                    }
                    // Configuration permission never changes the strict matching result.
                    assertEquals(
                        valid,
                        RuleScopePolicy.appVersionMatches(app, app.rules.single(), info),
                    )
                    assertEquals(
                        valid,
                        RuleScopePolicy.versionMatches(
                            RuleScopePolicy.globalApp(global, global.rules.single(), info.id)!!,
                            info,
                        ),
                    )
                    val summary =
                        RuleSummaryBuilder.build(
                            RuleRuntime(),
                            listOf(UsedSubsEntry(configs.subsItems.single(), subscription)),
                            mapOf(info.id to info),
                            configs.appConfigs,
                            configs.appGroupConfigs + configs.globalGroupConfigs,
                            configs.categoryConfigs,
                        )
                    assertEquals(
                        enabled && valid,
                        summary.appIdToRules[info.id].orEmpty().isNotEmpty(),
                    )
                    assertEquals(
                        enabled && valid,
                        summary.globalRules.any { it.matchActivity(info.id, "app.id.Main") },
                    )
                }
            }
        }
    }

    @Test
    fun invalidDefinitionsDoNotBypassRealVersionRestrictionsWhenSavingPreferences() {
        // Even an invalid pattern cannot override explicit code/name lists; valid patterns still
        // restrict.
        val conditions =
            listOf(
                "versionCode:{minimum:20},versionName:{pattern:'['}",
                "versionName:{pattern:'[',include:['2.0']}",
                "versionName:{pattern:'[',exclude:['1.0']}",
                "versionName:{pattern:'2[.].*'}",
            )
        for (condition in conditions) {
            val definition = "{matches:'*',position:{x:'unknown',y:0}}"
            val subscription =
                RawSubscriptionParser.parse(
                    """{id:-2,name:'Test',version:1,
                apps:[{id:'app.id',groups:[{key:2,name:'App',$condition,rules:[$definition]}]}],
                globalGroups:[{key:3,name:'Global',apps:[{id:'app.id',$condition}],rules:[$definition]}]}""",
                )
            for (group in
                listOf(
                    subscription.apps.single().groups.single(),
                    subscription.globalGroups.single(),
                )) {
                val state =
                    RuleGroupPolicy.controlState(
                        subscription,
                        group,
                        info.id,
                        initial,
                        info,
                        "launcher",
                        emptySet(),
                    )
                assertFalse(group.valid)
                assertFalse(state.canEnable)
                assertFalse(state.available)
                assertTrue(RuleRestriction.VersionMismatch in state.restrictions)
            }
        }
    }

    @get:Rule val temporaryFolder = TemporaryFolder(TestFiles.root)

    private val sub =
        RawSubscriptionParser.parse(
            """{
        id: -2, name: 'Test', version: 0,
        categories: [{key: 1, name: 'App', enable: true}],
        apps: [{id: 'app.id', groups: [{key: 2, name: 'App group', rules: ['[text="Ad"]']}]}],
        globalGroups: [{key: 3, name: 'Global', rules: [{matches: '[text="Ad"]'}]}]
    }""",
        )
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)
    private val initial =
        SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)))

    @Test
    fun manualEnableIsRecordedEvenWhenTheGlobalDefaultAlreadyEnablesIt() {
        val group = sub.globalGroups.single()
        val target = RuleSwitchTarget.GlobalGroup(sub.id, group.key)
        val next =
            RuleSwitchPolicy.updateGroup(
                target,
                SubsGlobalGroupConfig(sub.id, group.key),
                RuleSetting.Enabled,
            ) as SubsGlobalGroupConfig
        val configs = initial.copy(globalGroupConfigs = listOf(next))
        assertEquals(RuleSetting.Enabled, RuleConfigIndex(configs).setting(target))
        assertTrue(RuleGroupPolicy.getGroupEnabled(group.copy(enable = false), next))
        assertEquals(RuleSetting.FollowDefault, RuleConfigIndex(initial).setting(target))
    }

    @Test
    fun globalAppResetOnlyRemovesThatAppsOverrideAndKeepsTheTotalSwitchAndPages() {
        val original =
            SubsGlobalGroupConfig(sub.id, 3, false, "!app.id\nother.app\napp.id/app.id.Page")
        val target = RuleSwitchTarget.GlobalApp(sub.id, 3, "app.id")
        val enabled =
            RuleSwitchPolicy.updateGroup(target, original.copy(exclude = ""), RuleSetting.Enabled)
        assertEquals(false, ExcludeData.parse(enabled.exclude).appIds["app.id"])
        val reset = RuleSwitchPolicy.updateGroup(target, original, RuleSetting.FollowDefault)
        assertEquals(false, reset.enable)
        assertEquals(mapOf("other.app" to true), ExcludeData.parse(reset.exclude).appIds)
        assertEquals(setOf("app.id" to "app.id.Page"), ExcludeData.parse(reset.exclude).activityIds)
        val totalReset =
            RuleSwitchPolicy.updateGroup(
                RuleSwitchTarget.GlobalGroup(sub.id, 3),
                original,
                RuleSetting.FollowDefault,
            )
        assertNull(totalReset.enable)
        assertEquals(original.exclude, totalReset.exclude)
    }

    @Test
    fun appGroupResetRestoresCategoryInheritanceAndPreservesPageExclusions() {
        val original = SubsAppGroupConfig(sub.id, "app.id", 2, false, "app.id.Page")
        val reset =
            RuleSwitchPolicy.updateGroup(
                RuleSwitchTarget.AppGroup(sub.id, "app.id", 2),
                original,
                RuleSetting.FollowDefault,
            )
        assertNull(reset.enable)
        assertEquals(original.exclude, reset.exclude)
        assertTrue(
            RuleGroupPolicy.getGroupEnabled(
                sub.apps.single().groups.single(),
                reset,
                sub.categories.single(),
            ),
        )
        assertFalse(
            RuleGroupPolicy.getGroupEnabled(
                sub.apps.single().groups.single(),
                reset,
                sub.categories.single(),
                SubsCategoryConfig(false, sub.id, 1),
            ),
        )
    }

    @Test
    fun closedParentsKeepTheChildPreferenceEditableAndVisibleAsRestricted() {
        val group = sub.apps.single().groups.single()
        val configs =
            initial.copy(
                subsItems = listOf(initial.subsItems.single().copy(enable = false)),
                appConfigs = listOf(SubsAppConfig(false, sub.id, "app.id")),
                appGroupConfigs = listOf(SubsAppGroupConfig(sub.id, "app.id", 2, true)),
            )
        val state =
            RuleGroupPolicy.controlState(
                sub,
                group,
                "app.id",
                configs,
                info,
                "launcher",
                emptySet(),
            )
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
        val state =
            RuleGroupPolicy.controlState(
                sub,
                group,
                "app.id",
                initial.copy(globalGroupConfigs = listOf(config)),
                info,
                "launcher",
                emptySet(),
            )
        assertTrue(state.configuredEnabled)
        assertFalse(state.available)
        assertTrue(state.restrictions.contains(RuleRestriction.GlobalGroupDisabled))
    }

    fun requestRejectsChangedSwitchesButAcceptsConcurrentPageAndOtherAppEdits() {
        val service = RuleGroupConfigService
        val target = RuleSwitchTarget.GlobalApp(sub.id, 3, "app.id")
        val request = service.prepare(listOf(target), listOf(sub), initial)
        val unrelated =
            initial.copy(
                globalGroupConfigs =
                    listOf(
                        SubsGlobalGroupConfig(
                            sub.id,
                            3,
                            false,
                            "other.app\napp.id/app.id.Page",
                        ),
                    ),
            )
        request.checkCurrent(RuleConfigIndex(unrelated))
        val changed =
            unrelated.copy(
                globalGroupConfigs =
                    listOf(
                        unrelated.globalGroupConfigs.single().copy(exclude = "app.id"),
                    ),
            )
        assertThrows(IllegalStateException::class.java) {
            request.checkCurrent(
                RuleConfigIndex(
                    changed,
                ),
            )
        }
    }
}
