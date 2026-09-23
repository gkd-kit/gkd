package li.gkd.app.domain.rule

import li.gkd.app.data.AppInfo
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleScopePolicyTest {
    private val empty = ExcludeData(emptyMap(), emptySet())
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)

    @Test
    fun globalPagePrefixesUseTheActivityAsTheHaystackAndBuiltinExclusionsAlsoApplyToManualIncludes() {
        val scope = GlobalAppScope(true, listOf("app.id.Allowed"), listOf("app.id.Allowed.Blocked"))
        fun matches(activity: String?, exclude: ExcludeData = empty) =
            RuleScopePolicy.matchGlobalActivity(scope, false, "app.id", activity, false, exclude)
        assertTrue(matches("app.id.Allowed.Child"))
        assertFalse(matches("app.id.All"))
        assertFalse(matches("app.id.Allowed.Blocked.Child"))
        assertTrue(matches(null))
        val include = ExcludeData(mapOf("app.id" to false), emptySet())
        assertTrue(matches("app.id.Other", include))
        assertFalse(matches("app.id.Allowed.Blocked.Child", include))
        assertFalse(RuleScopePolicy.matchGlobalActivity(scope.copy(enabled = false), true, "app.id", null, false, include))
        assertFalse(RuleScopePolicy.matchGlobalActivity(scope, true, "app.id", null, true, include))
    }

    @Test
    fun personalGlobalPageEntriesRetainTheirExactMatchContract() {
        val exclude = ExcludeData(emptyMap(), setOf("app.id" to "app.id.Page"))
        assertFalse(RuleScopePolicy.matchGlobalActivity(null, true, "app.id", "app.id.Page", false, exclude))
        assertTrue(RuleScopePolicy.matchGlobalActivity(null, true, "app.id", "app.id.Page.Child", false, exclude))
    }

    @Test
    fun childEmptyExclusionsReplaceGroupExclusionsAndOriginsStaySeparate() {
        val sub = RawSubscription.parse("""{
          id: -2, name: 'Test', version: 0,
          apps: [{id:'app.id', groups:[{key:1,name:'Group',excludeActivityIds:['.Parent'],rules:[
            {matches:'[text="Ad"]'},
            {matches:'[text="Ad"]',excludeActivityIds:[]},
            {matches:'[text="Ad"]',excludeActivityIds:['.Child'],excludeMatches:['[text="Skip"]']}
          ]}]}]
        }""")
        val group = sub.apps.single().groups.single()
        val limitations = RuleLimitationPolicy.resolve(sub, group, "app.id",
            ExcludeData(emptyMap(), setOf("app.id" to "app.id.Parent")), info)
        val pages = limitations.builtIn.filter { it.kind == RuleLimitationKind.ExcludedPagePrefix }
        assertEquals(listOf("app.id.Parent", "app.id.Child"), pages.map { it.value })
        assertEquals(RuleLimitationSource(), pages.first().source)
        assertEquals(setOf(1), pages.first().appliesTo)
        assertEquals(3, pages.last().source.ruleIndex)
        assertEquals("app.id.Parent", limitations.personal.single().value)
        assertFalse(limitations.fullyBlocked)
        val overridden = group.copy(rules = listOf(group.rules[1]))
        assertTrue(RuleLimitationPolicy.resolve(sub, overridden, "app.id", empty, info).builtIn.isEmpty())
    }

    @Test
    fun mixedGlobalChildScopesOnlyBlockTheGroupWhenEveryChildExcludesTheApp() {
        val sub = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          globalGroups:[{key:1,name:'Global',apps:[{id:'app.id',enable:false}],rules:[
            {matches:'[text="Ad"]'},
            {matches:'[text="Ad"]',apps:[]}
          ]}]
        }""")
        val group = sub.globalGroups.single()
        val configs = SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)),
            globalGroupConfigs = listOf(SubsGlobalGroupConfig(-2, 1, true, "!app.id")))
        val partial = RuleGroupPolicy.controlState(sub, group, "app.id", configs, info, "launcher", emptySet())
        assertEquals(1, partial.limitations.blockedRules)
        assertEquals(2, partial.limitations.ruleCount)
        assertTrue(partial.canEnable)
        assertTrue(partial.available)
        assertTrue(partial.restrictions.isEmpty())
        assertEquals(listOf("规则排除了当前应用"), partial.limitations.blockedReasons)
        val allExcluded = group.copy(rules = listOf(group.rules.first()))
        val blocked = RuleGroupPolicy.controlState(sub, allExcluded, "app.id", configs, info, "launcher", emptySet())
        assertFalse(blocked.canEnable)
        assertFalse(blocked.available)
        assertEquals(RuleSetting.Enabled, blocked.setting)
        assertEquals(listOf("规则排除了当前应用"), blocked.restrictions)
    }

    @Test
    fun defaultsUseTheVersionCompatibleChildrenAndMatchFlagsWhileManualOnRemainsAnOption() {
        val sub = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          globalGroups:[{key:1,name:'Global',rules:[
            {matches:'[text="Ad"]',apps:[{id:'app.id',versionCode:{minimum:20}}]},
            {matches:'[text="Ad"]',matchAnyApp:false}
          ]}]
        }""")
        val group = sub.globalGroups.single()
        val configs = SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)))
        val state = RuleGroupPolicy.controlState(sub, group, "app.id", configs, info, "launcher", emptySet())
        assertFalse(state.defaultEnabled)
        assertTrue(state.canEnable)
        assertEquals(1, state.limitations.blockedRules)
        val manual = RuleGroupPolicy.controlState(sub, group, "app.id", configs.copy(
            globalGroupConfigs = listOf(SubsGlobalGroupConfig(-2, 1, null, "!app.id"))), info, "launcher", emptySet())
        assertTrue(manual.available)
        assertEquals(listOf("当前应用版本不符合规则要求"), manual.limitations.blockedReasons)
    }

    @Test
    fun appRuleVersionFieldsInheritIndependentlyAndCanBeOverriddenByTheChild() {
        val sub = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[{key:1,name:'App',versionCode:{minimum:20},rules:[
            {matches:'[text="Ad"]'},
            {matches:'[text="Ad"]',versionCode:{minimum:1}}
          ]}]}]
        }""")
        val group = sub.apps.single().groups.single()
        assertFalse(RuleScopePolicy.appVersionMatches(group, group.rules[0], info))
        assertTrue(RuleScopePolicy.appVersionMatches(group, group.rules[1], info))
        assertTrue(RuleScopePolicy.appVersionMatches(group, group.rules[0], null))
        val limits = RuleLimitationPolicy.resolve(sub, group, "app.id", empty, info)
        assertEquals(1, limits.blockedRules)
        assertEquals(listOf("当前应用版本不符合规则要求"), limits.blockedReasons)
        assertEquals(setOf("至少 20", "至少 1"), limits.builtIn.map { it.value }.toSet())
    }

    @Test
    fun unavailableReasonsDistinguishAppGroupMatchingFromExplicitExclusionsAndVersionChecks() {
        val sub = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[{key:1,name:'广告-开屏',rules:[{matches:'[text="Ad"]'}]}]}],
          globalGroups:[{key:1,name:'Global',disableIfAppGroupMatch:'广告',
            rules:[{matches:'[text="Ad"]',apps:[{id:'app.id',enable:false}]}]}]
        }""")
        val group = sub.globalGroups.single()
        val configs = SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)))
        fun state(subscription: RawSubscription, raw: RawSubscription.RawGlobalGroup) =
            RuleGroupPolicy.controlState(subscription, raw, "app.id", configs, info, "launcher", emptySet())
        val matched = state(sub, group)
        assertFalse(matched.canEnable)
        assertEquals(listOf("当前应用存在匹配的应用规则组，按订阅设置停用此全局规则"), matched.restrictions)

        val ignored = sub.copy(apps = sub.apps.map { app ->
            app.copy(groups = app.groups.map { it.copy(ignoreGlobalGroupMatch = true) })
        })
        assertEquals(listOf("规则排除了当前应用"), state(ignored, group).restrictions)
        val versionGroup = group.copy(rules = listOf(group.rules.single().copy(apps = listOf(
            group.rules.single().apps!!.single().copy(enable = null,
                versionCode = RawSubscription.IntegerMatcher(20, null, null, null)),
        ))))
        assertEquals(listOf("当前应用版本不符合规则要求"), state(ignored, versionGroup).restrictions)
        val explicitlyEnabled = versionGroup.copy(rules = listOf(versionGroup.rules.single().copy(
            apps = versionGroup.rules.single().apps!!.map { it.copy(enable = true) },
        )))
        val available = state(ignored, explicitlyEnabled)
        assertTrue(available.canEnable)
        assertTrue(available.available)
        assertTrue(available.restrictions.isEmpty())
        assertTrue(available.limitations.blockedReasons.isEmpty())

        val emptyGroup = group.copy(apps = group.rules.single().apps, rules = emptyList())
        val emptyState = state(ignored, emptyGroup)
        assertFalse(emptyState.canEnable)
        assertEquals(listOf("规则排除了当前应用"), emptyState.restrictions)
    }
}
