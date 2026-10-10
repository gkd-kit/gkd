package li.gkd.app.ui.subscription

import li.gkd.app.subscription.RawSubscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import li.gkd.db.RuleGroupType

class AppRuleSectionsTest {
    private val first = RawSubscription.parse(
        """{id:1,name:'First',version:1,
          categories:[{key:1,name:'功能'},{key:2,name:'广告'},{key:3,name:'空类别'}],
          globalGroups:[{key:1,name:'全局',rules:[{matches:'*'}]}],
          apps:[{id:'app',groups:[
            {key:1,name:'广告-B',rules:[{matches:'*'}]},
            {key:2,name:'功能-B',rules:[{matches:'*'}]},
            {key:3,name:'其他',rules:[{matches:'*'}]},
            {key:4,name:'功能-A',rules:[{matches:'*'}]}
          ]}]}""",
    )
    private val second = RawSubscription.parse(
        """{id:2,name:'Second',version:1,
          categories:[{key:1,name:'独有'},{key:8,name:'功能'}],
          globalGroups:[{key:1,name:'全局',rules:[{matches:'*'}]}],
          apps:[{id:'app',groups:[
            {key:1,name:'功能-C',rules:[{matches:'*'}]},
            {key:2,name:'独有-A',rules:[{matches:'*'}]}
          ]}]}""",
    )

    private fun sources() = listOf(first, second).map { it to (it.globalGroups + it.getAppGroups("app")) }

    @Test
    fun categoryFilterSeparatesGlobalsFromMatchingAppRulesAcrossSubscriptions() {
        val sections = buildAppRuleSections(sources(), true)
        val filtered = filterAppRuleSections(sections, AppRuleCategorySelection.Category("功能"))
        assertEquals(listOf(1L, 2L), filtered.first().subscriptions.map { it.first.id })
        assertEquals(listOf("功能-B", "功能-A", "功能-C"),
            filtered.flatMap { it.subscriptions }.flatMap { it.second }.map { it.name })
        val uncategorized = filterAppRuleSections(sections, AppRuleCategorySelection.Category(null))
        assertEquals(listOf("其他"),
            uncategorized.flatMap { it.subscriptions }.flatMap { it.second }.map { it.name })
        assertEquals(sections, filterAppRuleSections(sections, null))
        val globals = filterAppRuleSections(sections, null, globalOnly = true)
        assertEquals(listOf("全局", "全局"),
            globals.flatMap { it.subscriptions }.flatMap { it.second }.map { it.name })
        assertTrue(filterAppRuleSections(
            sections.filter { it.type != AppRuleSectionType.Global }, null, globalOnly = true,
        ).isEmpty())
    }

    @Test
    fun focusChoosesCategoryBySubscriptionAndRuleTypeAndMissingCategoryFallsBackToAll() {
        val sections = buildAppRuleSections(sources(), true)
        assertEquals(AppRuleCategorySelection.Category("功能"), resolveAppRuleCategory(
            AppRuleCategorySelection.FocusedRule, sections, Triple(2L, RuleGroupType.App, 1)))
        assertEquals(AppRuleCategorySelection.Category(null), resolveAppRuleCategory(
            AppRuleCategorySelection.FocusedRule, sections, Triple(1L, RuleGroupType.App, 3)))
        assertNull(resolveAppRuleCategory(
            AppRuleCategorySelection.FocusedRule, sections, Triple(2L, RuleGroupType.Global, 1)))
        assertNull(resolveAppRuleCategory(
            AppRuleCategorySelection.Category("removed"), sections, null))
        assertEquals(AppRuleCategorySelection.Category("广告"), resolveAppRuleCategory(
            AppRuleCategorySelection.Category("广告"), sections, Triple(2L, RuleGroupType.App, 1)))
    }

    @Test
    fun mergesCategoryNamesWithoutMergingSubscriptionOrRuleIdentities() {
        val sections = buildAppRuleSections(sources(), true)
        assertEquals(
            listOf(AppRuleSectionType.Global, AppRuleSectionType.Category, AppRuleSectionType.Category,
                AppRuleSectionType.Category, AppRuleSectionType.Uncategorized),
            sections.map { it.type },
        )
        assertEquals(listOf("功能", "广告", "独有"), sections.mapNotNull { it.categoryName })
        val feature = sections[1]
        assertEquals(listOf(1L, 2L), feature.subscriptions.map { it.first.id })
        // Keep the incoming sort inside each subscription, even with overlapping group keys.
        assertEquals(listOf(listOf(2, 4), listOf(1)), feature.subscriptions.map { it.second.map { g -> g.key } })
        assertEquals(listOf(3), sections.last().subscriptions.single().second.map { it.key })
        assertEquals(8, sections.sumOf { it.groupCount })
    }

    @Test
    fun flatModeKeepsEachSubscriptionTogetherWithGlobalsFirst() {
        // An existing name/time sort may put app rules before global rules.
        val input = listOf(first, second).map { it to (it.getAppGroups("app").reversed() + it.globalGroups) }
        val section = buildAppRuleSections(input, false).single()
        assertEquals(listOf(1L, 2L), section.subscriptions.map { it.first.id })
        section.subscriptions.forEach { (subscription, groups) ->
            assertEquals(subscription.globalGroups + subscription.getAppGroups("app").reversed(), groups)
        }
        assertTrue(buildAppRuleSections(listOf(first to emptyList()), false).isEmpty())
    }

    @Test
    fun onlyVisibleMembersProduceSectionsAndPrefixMatchingKeepsFirstCategory() {
        val subscription = first.copy(categories = listOf(
            RawSubscription.RawCategory(1, "功", null, null),
            RawSubscription.RawCategory(2, "功能", null, null),
        ))
        val visible = subscription.getAppGroups("app").filter { it.key == 2 }
        val sections = buildAppRuleSections(listOf(subscription to visible), true)
        assertEquals(listOf("功"), sections.map { it.categoryName })
        assertEquals(1, sections.single().groupCount)
        assertTrue(buildAppRuleSections(listOf(subscription to emptyList()), true).isEmpty())
        val noCategories = subscription.copy(categories = emptyList())
        assertEquals(AppRuleSectionType.Uncategorized,
            buildAppRuleSections(listOf(noCategories to visible), true).single().type)
    }
}
