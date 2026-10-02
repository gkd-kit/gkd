package li.gkd.app.rule

import kotlin.test.Test
import kotlin.test.assertEquals
import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.app.ui.home.AppListSources
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubsItem

class RuleGroupSummaryTest {
    @Test
    fun summariesAggregateSubscriptionsAndReactToCategoryAndGroupChanges() {
        val policy = RuleGroupPolicy()

        fun sub(id: Long) =
            RawSubscription.parse("""{id:$id,name:'Test',version:1,categories:[{key:1,name:'Example'}],apps:[{id:'app.example',groups:[{key:1,name:'Example rule',rules:[{matches:'*'}]}]}]}""")

        val entries = listOf(1L, 2L).map {
            UsedSubsEntry(
                SubsItem(id = it, enable = true, order = 0),
                sub(it)
            )
        }
        val apps =
            mapOf("app.example" to AppInfo("app.example", "Example", 1, "1", false, 0, false, 0))

        fun summary(
            categories: List<SubsCategoryConfig> = emptyList(),
            groups: List<SubsGroupConfig> = emptyList()
        ) =
            RuleGroupSummaryBuilder.build(policy, entries, apps, emptyList(), groups, categories)
        assertEquals(2, summary().appGroupSize)
        assertEquals(1, summary().appSize)
        assertEquals(
            1,
            summary(
                listOf(
                    SubsCategoryConfig(
                        subsId = 1,
                        categoryKey = 1,
                        enable = false
                    )
                )
            ).appGroupSize
        )
        assertEquals(
            2,
            summary(
                listOf(SubsCategoryConfig(subsId = 1, categoryKey = 1, enable = false)),
                listOf(SubsAppGroupConfig(1, "app.example", 1, true))
            ).appGroupSize
        )
    }

    @Test
    fun appListProjectionCountsOnlyEnabledGroupsAndKeepsAppsWithoutRules() {
        val apps =
            listOf("app.example", "app.empty").map { AppInfo(it, it, 1, "1", false, 0, false, 0) }
        val subscription =
            RawSubscription.parse("""{id:1,name:'Test',version:1,apps:[{id:'app.example',groups:[{key:1,name:'On',rules:[{matches:'*'}]},{key:2,name:'Off',rules:[{matches:'*'}]}]}]}""")
        val summary =
            RuleGroupSummaryBuilder.build(
                RuleGroupPolicy(),
                listOf(UsedSubsEntry(SubsItem(id = 1, enable = true, order = 0), subscription)),
                apps.associateBy { it.id },
                emptyList(),
                listOf(SubsAppGroupConfig(1, "app.example", 2, false)),
                emptyList()
            )
        val source = AppListSources.build(
            apps, listOf("app.empty"), listOf("app.empty", "app.example"),
            mapOf("app.example" to 3), summary.appIdToAllGroups
        )
        assertEquals(1, source.ruleStats.getValue("app.example").appGroups)
        assertEquals(3, source.ruleStats.getValue("app.example").globalGroups)
        assertEquals(0, source.ruleStats.getValue("app.empty").appGroups)
        assertEquals(0, source.ruleStats.getValue("app.empty").globalGroups)
        assertEquals(mapOf("app.empty" to 0, "app.example" to 1), source.visitOrder)
        assertEquals(apps, source.apps)
    }

}
