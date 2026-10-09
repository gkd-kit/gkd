package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleControlExplanationTest {
    private val policy = RuleGroupPolicy()
    private val info = AppInfo("app.id", "App", 10, "1.0", false, 1, false, 0)
    private val subscription = RawSubscription.parse("""{
      id:1,name:'Test',version:1,categories:[{key:1,name:'Ad',enable:false}],
      apps:[{id:'app.id',groups:[{key:1,name:'Ad',rules:[{matches:'*'}]}]}],
      globalGroups:[{key:1,name:'Ad',matchAnyApp:false,disableIfAppGroupMatch:'',
        rules:[{matches:'*'}]}]
    }""")

    // The decisive step must agree with actual policy, across local/category/default precedence.
    @Test
    fun explanationTracksCategoryInheritanceAndManualOverrides() {
        val group = subscription.apps.single().groups.single()
        for (category in listOf(null, SubsCategoryConfig(null, 1, 1), SubsCategoryConfig(true, 1, 1))) {
            for (own in listOf(null, false, true)) {
                val config = SubscriptionConfigSnapshot(
                    categoryConfigs = listOfNotNull(category),
                    appGroupConfigs = listOf(SubsAppGroupConfig(1, info.id, 1, own)),
                )
                val control = policy.controlState(subscription, group, info.id, config, info, "", emptySet())
                val explanation = policy.explainControl(subscription, group, info.id, config, control)
                assertEquals(control.available, explanation.enabled)
                val expected = when {
                    own != null -> RuleControlStepKind.Own
                    category == null || category.enable != null -> RuleControlStepKind.Category
                    else -> RuleControlStepKind.GroupDefault
                }
                assertEquals(expected, explanation.steps[explanation.decidingIndex].kind)
            }
        }
    }

    // Per-app enable overrides default exclusions, but cannot bypass group/subscription gates or versions.
    @Test
    fun globalExplanationPreservesGatesAndHardRestrictions() {
        val original = subscription.globalGroups.single()
        val versionLimited = RawSubscription.parse("""{
          id:1,name:'Test',version:1,globalGroups:[{key:1,name:'Ad',
            apps:[{id:'app.id',versionCode:{minimum:20}}],rules:[{matches:'*'}]}]
        }""").globalGroups.single()
        for (group in listOf(original, versionLimited)) {
            for (subscriptionEnabled in listOf(false, true)) {
                for (groupEnabled in listOf(false, true)) {
                    for (exclude in listOf("", "!app.id", "app.id")) {
                        val config = SubscriptionConfigSnapshot(
                            subsItems = listOf(SubsItem(1, order = 0, enable = subscriptionEnabled)),
                            globalGroupConfigs = listOf(SubsGlobalGroupConfig(1, 1, groupEnabled, exclude)),
                        )
                        val control = policy.controlState(subscription, group, info.id, config, info, "", emptySet())
                        val explanation = policy.explainControl(subscription, group, info.id, config, control)
                        assertEquals(control.available, explanation.enabled)
                        if (!subscriptionEnabled) assertEquals(RuleControlStepKind.Subscription,
                            explanation.steps[explanation.decidingIndex].kind)
                        else if (!groupEnabled) assertEquals(RuleControlStepKind.GlobalGroup,
                            explanation.steps[explanation.decidingIndex].kind)
                        else if (group === versionLimited) assertEquals(RuleControlStepKind.Restrictions,
                            explanation.steps[explanation.decidingIndex].kind)
                        else if (exclude == "!app.id") assertEquals(RuleControlStepKind.GlobalApp,
                            explanation.steps[explanation.decidingIndex].kind)
                    }
                }
            }
        }
    }

    @Test
    fun configurationExplanationDoesNotTreatWhitelistAsAConfigurationGate() {
        val group = subscription.globalGroups.single()
        val config = SubscriptionConfigSnapshot(globalGroupConfigs = listOf(
            SubsGlobalGroupConfig(1, 1, exclude = "!app.id"),
        ))
        val control = policy.controlState(subscription, group, info.id, config, info, "", emptySet(), blockedApp = true)
        assertFalse(control.available)
        val explanation = policy.explainControl(subscription, group, info.id, config, control)
        assertTrue(explanation.enabled)
        assertEquals(null, explanation.steps[explanation.decidingIndex].note)
    }
}
