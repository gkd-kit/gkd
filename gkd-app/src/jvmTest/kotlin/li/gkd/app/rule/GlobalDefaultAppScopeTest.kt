package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalDefaultAppScopeTest {
    private val subscription = RawSubscription.parse("""{
      id:1,name:'Scope',version:1,globalGroups:[{
        key:1,name:'Ad',matchAnyApp:false,
        apps:[{id:'app.listed'},{id:'app.disabled',enable:false},
              {id:'app.version',enable:false,versionCode:{minimum:20}},
              {id:'app.page',enable:false,excludeActivityIds:['.Blocked']}],
        rules:[{matches:'*',matchAnyApp:true},{matches:'*'}]
      }]
    }""")
    private val group = subscription.globalGroups.single()
    private val item = SubsItem(1, order = 0, enable = true)
    private val apps = listOf("app.ordinary", "app.listed", "app.disabled", "app.version", "app.page", "app.system", "app.launcher")
        .associateWith { AppInfo(it, it, 10, "1.0", it == "app.system", 1, false, 0) }

    // Compare execution, per-app controls and group counts from the same saved configuration.
    private fun checkScope(config: SubsGlobalGroupConfig, appId: String, expectedRules: List<Boolean>) {
        val resolved = ResolvedGlobalGroup(group, subscription, item, config)
        val runtime = RuleRuntime(environment = { RuleMatchEnvironment("app.launcher", setOf("app.system")) })
        assertEquals(expectedRules, group.rules.map {
            GlobalRule(it, resolved, apps, runtime).matchActivity(appId, "$appId.Main")
        })
        val configs = SubscriptionConfigSnapshot(subsItems = listOf(item), globalGroupConfigs = listOf(config))
        val control = RuleGroupPolicy().controlState(
            subscription, group, appId, configs, apps[appId], "app.launcher", setOf("app.system"),
        )
        assertEquals(expectedRules.any { it }, control.available)
        val summary = RuleGroupSummaryBuilder.build(
            RuleGroupPolicy(), listOf(UsedSubsEntry(item, subscription)), apps,
            emptyList(), listOf(config), emptyList(), "app.launcher",
        )
        assertEquals(if (expectedRules.any { it }) 1 else 0, summary.appIdToGlobalGroupCount[appId])
    }

    @Test
    fun localDefaultOverridesGroupFlagAndObsoleteChildFlagIsIgnored() {
        val follow = SubsGlobalGroupConfig(1, 1)
        checkScope(follow, "app.ordinary", listOf(false, false))
        checkScope(follow.copy(matchAnyApp = false), "app.ordinary", listOf(false, false))
        checkScope(follow.copy(matchAnyApp = true), "app.ordinary", listOf(true, true))
        checkScope(follow.copy(matchAnyApp = false).copy(matchAnyApp = null), "app.ordinary", listOf(false, false))
    }

    @Test
    fun narrowerAppSettingsAndPlatformDefaultsKeepTheirExistingPriority() {
        listOf(null, false, true).forEach { value ->
            val config = SubsGlobalGroupConfig(1, 1, matchAnyApp = value)
            checkScope(config, "app.listed", listOf(true, true))
            checkScope(config, "app.disabled", listOf(false, false))
            checkScope(config.copy(exclude = "!app.disabled"), "app.disabled", listOf(true, true))
            checkScope(config.copy(exclude = "!app.ordinary"), "app.ordinary", listOf(true, true))
            checkScope(config.copy(exclude = "app.ordinary"), "app.ordinary", listOf(false, false))
            listOf("app.system", "app.launcher").forEach { appId ->
                checkScope(config, appId, listOf(false, false))
                checkScope(config.copy(exclude = "!$appId"), appId, listOf(true, true))
            }
            checkScope(config.copy(exclude = "!app.version"), "app.version", listOf(false, false))
            checkScope(config.copy(exclude = "!app.page"), "app.page", listOf(true, true))
            val resolved = ResolvedGlobalGroup(group, subscription, item, config.copy(exclude = "!app.page"))
            assertEquals(false, GlobalRule(group.rules.first(), resolved, apps, RuleRuntime())
                .matchActivity("app.page", "app.page.Blocked"))
        }
    }

    @Test
    fun localDefaultDoesNotEnableADisabledGroupAndIsExposedAsAPersonalProperty() {
        val config = SubsGlobalGroupConfig(1, 1, enable = false, matchAnyApp = true)
        val configs = SubscriptionConfigSnapshot(subsItems = listOf(item), globalGroupConfigs = listOf(config))
        val control = RuleGroupPolicy().controlState(
            subscription, group, "app.ordinary", configs, apps["app.ordinary"], "app.launcher", setOf("app.system"),
        )
        assertEquals(false, control.available)
        assertEquals(true, control.defaultEnabled)
        assertEquals(true, control.limitations.personal.single().appOverride)
        val summary = RuleGroupSummaryBuilder.build(
            RuleGroupPolicy(), listOf(UsedSubsEntry(item, subscription)), apps,
            emptyList(), listOf(config), emptyList(), "app.launcher",
        )
        assertEquals(emptyList<ResolvedGlobalGroup>(), summary.globalGroups)
    }
    // Reasons must follow the same scope priority as the effective default, including overlaps.
    @Test
    fun defaultOffReasonsRespectSpecificAppAndPlatformPriority() {
        fun reasons(appId: String, local: Boolean? = null) = RuleGroupPolicy().controlState(
            subscription, group, appId,
            SubscriptionConfigSnapshot(globalGroupConfigs = listOf(
                SubsGlobalGroupConfig(1, 1, matchAnyApp = local),
            )),
            apps[appId], "app.launcher", setOf("app.system", "app.launcher", "app.listed"),
        ).defaultOffReasons
        assertEquals(listOf(GlobalAppDefaultOffReason.Launcher), reasons("app.launcher", false))
        assertEquals(listOf(GlobalAppDefaultOffReason.SystemApp), reasons("app.system", false))
        assertEquals(listOf(GlobalAppDefaultOffReason.LocalDefault), reasons("app.ordinary", false))
        assertEquals(listOf(GlobalAppDefaultOffReason.SubscriptionDefault), reasons("app.ordinary"))
        assertEquals(emptyList<GlobalAppDefaultOffReason>(), reasons("app.ordinary", true))
        assertEquals(emptyList<GlobalAppDefaultOffReason>(), reasons("app.listed", false))
        assertEquals(listOf(GlobalAppDefaultOffReason.SubscriptionApp), reasons("app.disabled", false))
        assertEquals(emptyList<GlobalAppDefaultOffReason>(), reasons("app.version", false))
    }

    @Test
    fun subscriptionDisableAndSameNameGroupRemainSeparateReasons() {
        val sub = RawSubscription.parse("""{
          id:1,name:'Scope',version:1,
          apps:[{id:'app.disabled',groups:[{key:1,name:'Ad',rules:[{matches:'*'}]}]}],
          globalGroups:[{key:1,name:'Ad',matchAnyApp:false,disableIfAppGroupMatch:'',
            apps:[{id:'app.disabled',enable:false}],rules:[{matches:'*'}]}]
        }""")
        val control = RuleGroupPolicy().controlState(
            sub, sub.globalGroups.single(), "app.disabled", SubscriptionConfigSnapshot(),
            apps["app.disabled"], "app.disabled", setOf("app.disabled"),
        )
        assertEquals(false, control.defaultEnabled)
        assertEquals(listOf(
            GlobalAppDefaultOffReason.SubscriptionApp,
            GlobalAppDefaultOffReason.SameNameAppGroup,
        ), control.defaultOffReasons)
    }

}
