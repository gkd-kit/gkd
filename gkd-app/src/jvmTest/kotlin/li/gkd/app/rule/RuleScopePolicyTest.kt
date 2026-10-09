package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleScopePolicyTest {
    private val policy = RuleGroupPolicy()
    private val limitationsPolicy = RuleLimitationPolicy()

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
        assertTrue(
            RuleScopePolicy.matchGlobalActivity(
                scope.copy(enabled = false),
                true,
                "app.id",
                null,
                false,
                include
            )
        )
        assertTrue(RuleScopePolicy.matchGlobalActivity(scope, true, "app.id", null, true, include))
    }

    @Test
    fun personalGlobalPageEntriesRetainTheirExactMatchContract() {
        val exclude = ExcludeData(emptyMap(), setOf("app.id" to "app.id.Page"))
        assertFalse(
            RuleScopePolicy.matchGlobalActivity(
                null,
                true,
                "app.id",
                "app.id.Page",
                false,
                exclude
            )
        )
        assertTrue(
            RuleScopePolicy.matchGlobalActivity(
                null,
                true,
                "app.id",
                "app.id.Page.Child",
                false,
                exclude
            )
        )
    }

    @Test
    fun childEmptyExclusionsReplaceGroupExclusionsAndOriginsStaySeparate() {
        val sub = RawSubscription.parse(
            """{
          id: -2, name: 'Test', version: 0,
          apps: [{id:'app.id', groups:[{key:1,name:'Group',excludeActivityIds:['.Parent'],rules:[
            {matches:'[text="Ad"]'},
            {matches:'[text="Ad"]',excludeActivityIds:[]},
            {matches:'[text="Ad"]',excludeActivityIds:['.Child'],excludeMatches:['[text="Skip"]']}
          ]}]}]
        }"""
        )
        val group = sub.apps.single().groups.single()
        val limitations = limitationsPolicy.resolve(
            sub, group, "app.id",
            ExcludeData(emptyMap(), setOf("app.id" to "app.id.Parent")), info
        )
        val pages = limitations.builtIn.filter { it.kind == RuleLimitationKind.ExcludedPagePrefix }
        assertEquals(listOf("app.id.Parent", "app.id.Child"), pages.map { it.value })
        assertEquals(RuleLimitationSource(), pages.first().source)
        assertEquals(setOf(1), pages.first().appliesTo)
        assertEquals(3, pages.last().source.ruleIndex)
        assertEquals("app.id.Parent", limitations.personal.single().value)
        assertFalse(limitations.fullyBlocked)
        val overridden = group.copy(rules = listOf(group.rules[1]))
        assertTrue(
            limitationsPolicy.resolve(
                sub,
                overridden,
                "app.id",
                empty,
                info
            ).builtIn.isEmpty()
        )
    }

    @Test
    fun manualAppEnableOverridesGroupDisableEvenWithChildAppLists() {
        val sub = RawSubscription.parse(
            """{
          id:-2,name:'Test',version:0,
          globalGroups:[{key:1,name:'Global',apps:[{id:'app.id',enable:false}],rules:[
            {matches:'[text="Ad"]'},
            {matches:'[text="Ad"]',apps:[]}
          ]}]
        }"""
        )
        val group = sub.globalGroups.single()
        val configs = SubscriptionConfigSnapshot(
            subsItems = listOf(SubsItem(-2, order = 0, enable = true)),
            globalGroupConfigs = listOf(SubsGlobalGroupConfig(-2, 1, true, "!app.id"))
        )
        val partial =
            policy.controlState(sub, group, "app.id", configs, info, "launcher", emptySet())
        assertEquals(0, partial.limitations.blockedRules)
        assertEquals(2, partial.limitations.ruleCount)
        assertTrue(partial.canEnable)
        assertTrue(partial.available)
        assertTrue(partial.restrictions.isEmpty())
        assertTrue(partial.limitations.blockedReasons.isEmpty())
        val allExcluded = group.copy(rules = listOf(group.rules.first()))
        val blocked =
            policy.controlState(sub, allExcluded, "app.id", configs, info, "launcher", emptySet())
        assertTrue(blocked.canEnable)
        assertTrue(blocked.available)
        assertEquals(RuleSetting.Enabled, blocked.setting)
        assertTrue(blocked.restrictions.isEmpty())
        val following = policy.controlState(
            sub, allExcluded, "app.id", configs.copy(globalGroupConfigs = emptyList()),
            info, "launcher", emptySet()
        )
        assertTrue(following.canEnable)
        assertFalse(following.configuredEnabled)
        assertFalse(following.available)
    }

    @Test
    fun defaultsUseGroupMatchFlagsAndVersionCompatibleChildren() {
        val sub = RawSubscription.parse(
            """{
          id:-2,name:'Test',version:0,
          globalGroups:[{key:1,name:'Global',matchAnyApp:false,rules:[
            {matches:'[text="Ad"]',apps:[{id:'app.id',versionCode:{minimum:20}}]},
            {matches:'[text="Ad"]',matchAnyApp:false}
          ]}]
        }"""
        )
        val group = sub.globalGroups.single()
        val configs =
            SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)))
        val state = policy.controlState(sub, group, "app.id", configs, info, "launcher", emptySet())
        assertFalse(state.defaultEnabled)
        assertTrue(state.canEnable)
        assertEquals(1, state.limitations.blockedRules)
        val manual = policy.controlState(
            sub, group, "app.id", configs.copy(
                globalGroupConfigs = listOf(SubsGlobalGroupConfig(-2, 1, null, "!app.id"))
            ), info, "launcher", emptySet()
        )
        assertTrue(manual.available)
        assertEquals(listOf(RuleRestriction.VersionMismatch), manual.limitations.blockedReasons)
    }

    @Test
    fun appRuleVersionFieldsInheritIndependentlyAndCanBeOverriddenByTheChild() {
        val sub = RawSubscription.parse(
            """{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[{key:1,name:'App',versionCode:{minimum:20},rules:[
            {matches:'[text="Ad"]'},
            {matches:'[text="Ad"]',versionCode:{minimum:1}}
          ]}]}]
        }"""
        )
        val group = sub.apps.single().groups.single()
        assertFalse(RuleScopePolicy.appVersionMatches(group, group.rules[0], info))
        assertTrue(RuleScopePolicy.appVersionMatches(group, group.rules[1], info))
        assertTrue(RuleScopePolicy.appVersionMatches(group, group.rules[0], null))
        val limits = limitationsPolicy.resolve(sub, group, "app.id", empty, info)
        assertEquals(1, limits.blockedRules)
        assertEquals(listOf(RuleRestriction.VersionMismatch), limits.blockedReasons)
        assertEquals(setOf(20, 1), limits.builtIn.map { it.versionCode?.minimum }.toSet())
    }

    @Test
    fun unavailableReasonsDistinguishAppGroupMatchingFromExplicitExclusionsAndVersionChecks() {
        val sub = RawSubscription.parse(
            """{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[{key:1,name:'广告-开屏',rules:[{matches:'[text="Ad"]'}]}]}],
          globalGroups:[{key:1,name:'Global',disableIfAppGroupMatch:'广告',apps:[{id:'app.id',enable:false}],
            rules:[{matches:'[text="Ad"]',apps:[{id:'app.id',enable:false}]}]}]
        }"""
        )
        val group = sub.globalGroups.single()
        val configs =
            SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0, enable = true)))

        fun state(subscription: RawSubscription, raw: RawSubscription.RawGlobalGroup) =
            policy.controlState(subscription, raw, "app.id", configs, info, "launcher", emptySet())

        val matched = state(sub, group)
        assertTrue(matched.canEnable)
        assertFalse(matched.defaultEnabled)
        assertTrue(matched.restrictions.isEmpty())
        assertEquals(listOf(RuleRestriction.ShadowedByAppRule), matched.limitations.blockedReasons)

        val ignored = sub.copy(apps = sub.apps.map { app ->
            app.copy(groups = app.groups.map { it.copy(ignoreGlobalGroupMatch = true) })
        })
        assertTrue(state(ignored, group).canEnable)
        assertEquals(listOf(RuleRestriction.AppExcluded), state(ignored, group).limitations.blockedReasons)
        val versionGroup = group.copy(
            rules = listOf(
                group.rules.single().copy(
                    apps = listOf(
                        group.rules.single().apps!!.single().copy(
                            versionCode = RawSubscription.IntegerMatcher(20, null, null, null)
                        ),
                    )
                )
            )
        )
        assertEquals(
            listOf(RuleRestriction.VersionMismatch),
            state(ignored, versionGroup).restrictions
        )
        val explicitlyEnabled = versionGroup.copy(
            apps = versionGroup.apps!!.map { it.copy(enable = true) },
        )
        val unavailable = state(ignored, explicitlyEnabled)
        assertFalse(unavailable.canEnable)
        assertFalse(unavailable.available)
        assertEquals(listOf(RuleRestriction.VersionMismatch), unavailable.restrictions)

        val emptyGroup = group.copy(rules = emptyList())
        val emptyState = state(ignored, emptyGroup)
        assertTrue(emptyState.canEnable)
        assertFalse(emptyState.defaultEnabled)
        assertTrue(emptyState.restrictions.isEmpty())
    }

    @Test
    fun manualAppEnablePreservesVersionAndPageExclusionsAndResetRestoresDefaultDisable() {
        // Existing !app.id values now override defaults, but never an incompatible version or excluded page.
        val sub = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[{key:1,name:'Ad',rules:[{matches:'*'}]}]}],
          globalGroups:[{key:1,name:'Ad',disableIfAppGroupMatch:'',
            apps:[{id:'app.id',enable:false,versionCode:{minimum:20},excludeActivityIds:['.Blocked']}],
            rules:[{matches:'*'}]}]
        }""")
        val group = sub.globalGroups.single()
        val target = RuleSwitchTarget.GlobalApp(-2, 1, "app.id")
        val stored = SubsGlobalGroupConfig(-2, 1, null, "!app.id\napp.id/app.id.Personal")
        val compatible = info.copy(versionCode = 20)
        fun matches(config: SubsGlobalGroupConfig, appInfo: AppInfo, page: String) =
            RuleScopePolicy.matchGlobalActivity(
                RuleScopePolicy.globalScope(group.apps!!.single(), appInfo), true,
                "app.id", page, true, ExcludeData.parse(config.exclude),
            )
        assertFalse(matches(stored, info, "app.id.Other"))
        assertTrue(matches(stored, compatible, "app.id.Other"))
        assertFalse(matches(stored, compatible, "app.id.Blocked.Child"))
        assertFalse(matches(stored, compatible, "app.id.Personal"))
        val reset = RuleSwitchPolicy.updateGroup(target, stored, RuleSetting.FollowDefault) as SubsGlobalGroupConfig
        assertFalse(matches(reset, compatible, "app.id.Other"))
        val disabled = RuleSwitchPolicy.updateGroup(target, stored, RuleSetting.Disabled) as SubsGlobalGroupConfig
        assertFalse(matches(disabled, compatible, "app.id.Other"))
        val configs = SubscriptionConfigSnapshot(
            subsItems = listOf(SubsItem(-2, order = 0, enable = true)),
            globalGroupConfigs = listOf(stored),
        )
        val incompatible = policy.controlState(sub, group, "app.id", configs, info, "launcher", emptySet())
        assertFalse(incompatible.canEnable)
        assertEquals(listOf(RuleRestriction.VersionMismatch), incompatible.restrictions)
        assertTrue(policy.controlState(sub, group, "app.id", configs, compatible, "launcher", emptySet()).available)
        assertFalse(policy.controlState(sub, group, "app.id", configs.copy(globalGroupConfigs = listOf(reset)), compatible, "launcher", emptySet()).available)
    }
}
