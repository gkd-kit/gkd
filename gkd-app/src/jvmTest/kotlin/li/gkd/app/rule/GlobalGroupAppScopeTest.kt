package li.gkd.app.rule

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.*
import org.junit.Test

class GlobalGroupAppScopeTest {
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)
    private val item = SubsItem(1, order = 0, enable = true)
    private fun subscription(body: String) = RawSubscription.parse(
        "{id:1,name:'Scope',version:1,globalGroups:[{key:1,name:'Group',$body}]}"
    )

    // The same parsed subscription must produce consistent execution, controls and counts.
    private fun check(sub: RawSubscription, expected: List<Boolean>, config: SubsGlobalGroupConfig = SubsGlobalGroupConfig(1, 1)) {
        val group = sub.globalGroups.single()
        val resolved = ResolvedGlobalGroup(group, sub, item, config)
        val apps = mapOf(info.id to info)
        assertEquals(expected, group.rules.map {
            GlobalRule(it, resolved, apps, RuleRuntime()).matchActivity(info.id, "app.id.Main")
        })
        val control = RuleGroupPolicy().controlState(
            sub, group, info.id,
            SubscriptionConfigSnapshot(subsItems = listOf(item), globalGroupConfigs = listOf(config)),
            info, "launcher", emptySet(),
        )
        assertEquals(expected.any { it }, control.available)
        val summary = RuleGroupSummaryBuilder.build(
            RuleGroupPolicy(), listOf(UsedSubsEntry(item, sub)), apps,
            emptyList(), listOf(config), emptyList(), "launcher",
        )
        assertEquals(if (expected.any { it }) 1 else 0, summary.appIdToGlobalGroupCount[info.id])
    }

    @Test
    fun obsoleteChildFieldsAreIgnoredEvenWhenMalformedAndDoNotRoundTrip() {
        val sub = subscription("""matchAnyApp:false,rules:[{
          matches:'*',matchAnyApp:'ignored',matchSystemApp:{},matchLauncher:[],
          apps:[{id:'app.id',enable:'ignored'}]
        }]""")
        check(sub, listOf(false))
        check(sub, listOf(true), SubsGlobalGroupConfig(1, 1, exclude = "!app.id"))
        val rule = sub.globalGroups.single().cacheJsonObject["rules"]!!.jsonArray.single().jsonObject
        listOf("matchAnyApp", "matchSystemApp", "matchLauncher").forEach { assertFalse(rule.containsKey(it)) }
        assertFalse(rule["apps"]!!.jsonArray.single().jsonObject.containsKey("enable"))
    }

    @Test
    fun childAppListsCannotEnableOrDisableAppsOrClearGroupDisables() {
        val rules = """rules:[
          {matches:'*',apps:[{id:'app.id',enable:true}]},
          {matches:'*',apps:[{id:'app.id',enable:false}]},
          {matches:'*',apps:[]}
        ]"""
        check(subscription("matchAnyApp:false,$rules"), listOf(false, false, false))
        check(subscription("matchAnyApp:true,$rules"), listOf(true, true, true))
        val disabled = subscription("apps:[{id:'app.id',enable:false}],$rules")
        check(disabled, listOf(false, false, false))
        check(disabled, listOf(true, true, true), SubsGlobalGroupConfig(1, 1, exclude = "!app.id"))
    }

    @Test
    fun childPlatformFlagsCannotOverrideTheGroupForSystemAppsAndLauncher() {
        val sub = subscription("""matchAnyApp:true,matchSystemApp:false,matchLauncher:false,
          rules:[{matches:'*',matchSystemApp:true,matchLauncher:true,apps:[{id:'app.id'}]}]""")
        val group = sub.globalGroups.single()
        val runtime = RuleRuntime(environment = { RuleMatchEnvironment(info.id, setOf(info.id)) })
        fun matches(raw: RawSubscription.RawGlobalGroup) = GlobalRule(
            raw.rules.single(), ResolvedGlobalGroup(raw, sub, item, null), mapOf(info.id to info), runtime,
        ).matchActivity(info.id, "app.id.Main")
        assertFalse(matches(group))
        assertTrue(matches(group.copy(matchSystemApp = true, matchLauncher = true)))
    }

    @Test
    fun childConditionsInheritPerAppAndFieldAndEmptyPageArraysClearOnlyThatField() {
        val sub = subscription("""matchAnyApp:false,apps:[
          {id:'app.id',enable:true,activityIds:['.Main'],excludeActivityIds:['.Blocked'],
           versionCode:{minimum:20},versionName:{include:['1.0']}},
          {id:'other.app',excludeActivityIds:['.Other']}
        ],rules:[
          {matches:'*',apps:[{id:'app.id',versionCode:{minimum:1},excludeActivityIds:[]}]},
          {matches:'*',apps:[]}
        ]""")
        val group = sub.globalGroups.single()
        val app = RuleScopePolicy.globalApp(group, group.rules.first(), info.id)!!
        assertEquals(listOf(".Main"), app.activityIds)
        assertEquals(emptyList<String>(), app.excludeActivityIds)
        assertEquals(listOf("1.0"), app.versionName!!.include)
        assertEquals(listOf(".Other"), RuleScopePolicy.globalApp(group, group.rules.first(), "other.app")!!.excludeActivityIds)
        check(sub, listOf(true, false))
        val limits = RuleLimitationPolicy().resolve(sub, group, info.id, ExcludeData(emptyMap(), emptySet()), info)
        assertEquals(1, limits.blockedRules)
        assertEquals(listOf(RuleRestriction.VersionMismatch), limits.blockedReasons)
        val code = limits.builtIn.single { it.versionCode?.minimum == 1 }
        assertEquals(1, code.source.ruleIndex)
        val name = limits.builtIn.single { it.kind == RuleLimitationKind.VersionName }
        assertNull(name.source.ruleIndex)
        assertEquals(setOf(1, 2), name.appliesTo)
        val excluded = limits.builtIn.single { it.kind == RuleLimitationKind.ExcludedPagePrefix }
        assertEquals(setOf(2), excluded.appliesTo)
    }

    @Test
    fun inheritedPageRestrictionsRemainEffectiveAndCanBeExplicitlyCleared() {
        val sub = subscription("""apps:[{id:'app.id',activityIds:['.Main'],excludeActivityIds:['.Main.Blocked']}],
          rules:[{matches:'*',apps:[]},
                 {matches:'*',apps:[{id:'app.id',activityIds:[],excludeActivityIds:[]}]}]""")
        val group = sub.globalGroups.single()
        val resolved = ResolvedGlobalGroup(group, sub, item, null)
        val rules = group.rules.map { GlobalRule(it, resolved, mapOf(info.id to info), RuleRuntime()) }
        assertEquals(listOf(true, true), rules.map { it.matchActivity(info.id, "app.id.Main") })
        assertEquals(listOf(false, true), rules.map { it.matchActivity(info.id, "app.id.Main.Blocked.Child") })
        assertEquals(listOf(false, true), rules.map { it.matchActivity(info.id, "app.id.Other") })
    }

    @Test
    fun explicitEnableNeverBypassesVersionConditions() {
        val sub = subscription("""apps:[{id:'app.id',enable:true,versionCode:{minimum:20}}],
          rules:[{matches:'*',apps:[{id:'app.id',enable:true}]}]""")
        check(sub, listOf(false))
        check(sub, listOf(false), SubsGlobalGroupConfig(1, 1, exclude = "!app.id", matchAnyApp = true))
    }
}
