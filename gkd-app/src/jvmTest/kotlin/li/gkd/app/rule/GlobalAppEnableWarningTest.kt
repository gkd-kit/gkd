package li.gkd.app.rule

import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalAppEnableWarningTest {
    @Test
    fun warningsCombineBuiltinDisablesAndBothNameContainmentDirectionsWithinTheSameApp() {
        val subscription = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          apps:[
            {id:'app.id',groups:[
              {key:1,name:'开屏广告',rules:[{matches:'*'}]},
              {key:2,name:'广告',enable:false,rules:[{matches:'*'}]},
              {key:3,name:'开屏广告-跳过',rules:[{matches:'*'}]},
              {key:4,name:'签到',rules:[{matches:'*'}]}
            ]},
            {id:'other.app',groups:[{key:1,name:'开屏广告-其他应用',rules:[{matches:'*'}]}]}
          ],
          globalGroups:[
            {key:1,name:'开屏广告',apps:[{id:'app.id',enable:false}],rules:[{matches:'*'}]},
            {key:2,name:'无关',apps:[{id:'app.id',enable:false}],rules:[{matches:'*',apps:[]}]},
            {key:3,name:'其他',rules:[{matches:'*',apps:[{id:'app.id',enable:false}]}]},
            {key:4,name:'开屏广告',disableIfAppGroupMatch:'',rules:[{matches:'*'}]},
            {key:5,name:'开屏广告',rules:[{matches:'*'}]}
          ]
        }""")
        val configs = SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0)))
        val targets = (1..5).map { RuleSwitchTarget.GlobalApp(-2, it, "app.id") }
        val warnings = RuleGroupConfigService.prepare(targets, listOf(subscription), configs).enableWarnings
        assertEquals(listOf(1, 3, 4, 5), warnings.map { it.target.groupKey })
        val combined = warnings.first()
        assertTrue(combined.builtInDisabled)
        assertEquals(listOf("开屏广告", "广告", "开屏广告-跳过"), combined.similarGroups.map { it.name })
        assertEquals(listOf(true, false, true), combined.similarGroups.map { it.enabled })
        assertTrue(warnings[1].builtInDisabled)
        assertTrue(warnings[1].similarGroups.isEmpty())
        assertFalse(warnings[2].builtInDisabled)
        assertTrue(warnings[2].excludedByGroupName)
        assertFalse(warnings[3].builtInDisabled)
        assertFalse(warnings[3].excludedByGroupName)
        assertTrue(warnings[3].similarGroups.isNotEmpty())
        // A no-op manual enable must not repeatedly request confirmation.
        val alreadyEnabled = configs.copy(globalGroupConfigs = listOf(SubsGlobalGroupConfig(-2, 1, exclude = "!app.id")))
        assertTrue(RuleGroupConfigService.prepare(listOf(targets.first()), listOf(subscription), alreadyEnabled).enableWarnings.isEmpty())
        assertTrue(RuleGroupConfigService.prepare(listOf(RuleSwitchTarget.GlobalGroup(-2, 1)), listOf(subscription), configs).enableWarnings.isEmpty())
    }
    @Test
    fun builtinDisableCountsRespectChildOverridesAndIgnoreVersionAndPageRestrictions() {
        val subscription = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,globalGroups:[
            {key:1,name:'All',apps:[{id:'app.id',enable:false}],rules:[{matches:'*'},{matches:'*'}]},
            {key:2,name:'Partial',apps:[{id:'app.id',enable:false}],rules:[{matches:'*'},{matches:'*',apps:[]}]},
            {key:3,name:'Mixed',rules:[
              {matches:'*',apps:[{id:'app.id',enable:false}]},
              {matches:'*',apps:[{id:'app.id',versionCode:{minimum:123},excludeActivityIds:['.Page']}]}
            ]}
          ]
        }""")
        val warnings = RuleGroupConfigService.prepare(
            (1..3).map { RuleSwitchTarget.GlobalApp(-2, it, "app.id") },
            listOf(subscription), SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0))),
        ).enableWarnings
        assertEquals(listOf(2, 1, 1), warnings.map { it.disabledRuleCount })
        assertEquals(listOf(2, 2, 2), warnings.map { it.ruleCount })
    }

    @Test
    fun warningListIncludesRulesThatDisableByCustomNameEvenWithoutSimilarNames() {
        val subscription = RawSubscription.parse("""{
          id:-2,name:'Test',version:0,
          apps:[{id:'app.id',groups:[
            {key:1,name:'专用跳过',rules:[{matches:'*'}]},
            {key:2,name:'专用关闭',ignoreGlobalGroupMatch:true,rules:[{matches:'*'}]}
          ]}],
          globalGroups:[{key:1,name:'开屏广告',disableIfAppGroupMatch:'专用',rules:[{matches:'*'}]}]
        }""")
        val warning = RuleGroupConfigService.prepare(
            listOf(RuleSwitchTarget.GlobalApp(-2, 1, "app.id")), listOf(subscription),
            SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(-2, order = 0))),
        ).enableWarnings.single()
        assertTrue(warning.excludedByGroupName)
        assertEquals(listOf("专用跳过"), warning.similarGroups.map { it.name })
    }

}
