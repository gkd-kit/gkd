package li.gkd.app.rule

import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RuleSummaryBuilderTest {
    private val policy = RuleGroupPolicy()
    private val runtime = RuleRuntime()

    @Test
    fun enabledGroupWithoutEnabledRulesDoesNotCountAsRuleApp() {
        val subscription = RawSubscription.parse(
            """
            {
              id: 1,
              name: 'Empty rules',
              version: 1,
              apps: [{
                id: 'app.id',
                groups: [{ key: 1, name: 'Group', rules: [] }],
              }],
            }
            """.trimIndent(),
        )

        val summary = RuleSummaryBuilder.build(

            policy, runtime,
            subscriptions = listOf(
                UsedSubsEntry(
                    subsItem = SubsItem(id = subscription.id, enable = true, order = 0),
                    subscription = subscription,
                ),
            ),
            appInfoById = emptyMap(),
            appConfigs = listOf(
                SubsAppConfig(
                    enable = true,
                    subsId = subscription.id,
                    appId = "app.id",
                ),
            ),
            groupConfigs = emptyList(),
            categoryConfigs = emptyList(),
        )

        assertFalse(summary.appIdToRules.containsKey("app.id"))
        assertEquals(1, summary.appIdToGroups.getValue("app.id").size)
        assertEquals(0, summary.appSize)
    }
}
