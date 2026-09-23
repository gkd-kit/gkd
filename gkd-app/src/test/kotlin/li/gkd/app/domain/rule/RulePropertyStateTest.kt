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

class RulePropertyStateTest {
    private val emptyExclusions = ExcludeData(emptyMap(), emptySet())
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)
    private val subscription = RawSubscription.parse("""{
      id:-2,name:'Test',version:0,
      globalGroups:[{key:1,name:'Global',rules:[{matches:'[text="Ad"]'}]}]
    }""")
    private val group = subscription.globalGroups.single()

    private fun state(config: SubsGlobalGroupConfig?, appId: String? = "app.id") =
        RuleGroupPolicy.controlState(subscription, group, appId,
            SubscriptionConfigSnapshot(
                subsItems = listOf(SubsItem(-2, order = 0, enable = true)),
                globalGroupConfigs = listOfNotNull(config),
            ), info, "launcher", emptySet())

    @Test
    fun appSwitchOverrideIsMarkedOnceAndOnlyInItsOwnScope() {
        val config = SubsGlobalGroupConfig(-2, 1, null, "!app.id")
        val app = state(config)
        assertTrue(app.defaultEnabled)
        assertTrue(app.configuredEnabled)
        assertTrue(app.hasCustomSetting)
        assertFalse(app.limitations.hasPersonalProperties)
        val global = state(config, appId = null)
        assertFalse(global.hasCustomSetting)
        assertTrue(global.limitations.hasPersonalProperties)
        assertFalse(state(config, "other.app").hasCustomSetting)
        assertFalse(state(config, "other.app").limitations.hasPersonalProperties)
    }

    @Test
    fun resettingAppSwitchKeepsItsPageExclusionAndDoesNotExposeOtherAppsExclusions() {
        val exclude = ExcludeData(mapOf("app.id" to false),
            setOf("app.id" to "app.id.Page", "other.app" to "other.app.Page"))
        val config = SubsGlobalGroupConfig(-2, 1, null, exclude.stringify())
        assertTrue(state(config).hasCustomSetting)
        assertTrue(state(config).limitations.hasPersonalProperties)
        val reset = RuleSwitchPolicy.updateGroup(RuleSwitchTarget.GlobalApp(-2, 1, "app.id"),
            config, RuleSetting.FollowDefault) as SubsGlobalGroupConfig
        val after = state(reset)
        assertFalse(after.hasCustomSetting)
        assertTrue(after.limitations.hasPersonalProperties)
        assertEquals("app.id.Page", after.limitations.personal.single().value)
        assertEquals(RuleLimitationKind.ExcludedPageExact, after.limitations.personal.single().kind)
        assertEquals(exclude.activityIds, ExcludeData.parse(reset.exclude).activityIds)
    }

    @Test
    fun implicitGlobalDefaultsDoNotLookLikeExtraSubscriptionRestrictions() {
        val implicit = RuleLimitationPolicy.resolve(subscription, group, "app.id", emptyExclusions, info)
        assertTrue(implicit.builtIn.isNotEmpty()) // They remain available in the details.
        assertFalse(implicit.hasBuiltInProperties)
        val explicit = RuleLimitationPolicy.resolve(subscription,
            group.copy(matchSystemApp = false), "app.id", emptyExclusions, info)
        assertTrue(explicit.hasBuiltInProperties)
    }

    @Test
    fun inheritedExclusionDisappearsWhenEveryChildExplicitlyClearsIt() {
        val sub = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[{key:1,name:'Group',excludeActivityIds:['.Blocked'],
            rules:[{matches:'[text="Ad"]',excludeActivityIds:[]}]}]}]
        }""")
        val raw = sub.apps.single().groups.single()
        val cleared = RuleLimitationPolicy.resolve(sub, raw, "app.id", emptyExclusions, info)
        assertFalse(cleared.hasBuiltInProperties)
        val inherited = RuleLimitationPolicy.resolve(sub,
            raw.copy(rules = listOf(raw.rules.single().copy(excludeActivityIds = null))),
            "app.id", emptyExclusions, info)
        assertTrue(inherited.hasBuiltInProperties)
        assertEquals(RuleLimitationKind.ExcludedPagePrefix, inherited.builtIn.single().kind)
    }
}
