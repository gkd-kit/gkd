package li.gkd.app.subscription

import kotlin.test.assertIs
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryPolicyTest {

    private val subscription =
        RawSubscriptionParser.parse(
            """
            {
              id: -2, name: 'Local', version: 0,
              categories: [
                { key: 0, name: '广告', enable: false },
                { key: 1, name: '广告-开屏', enable: true },
              ],
              apps: [
                { id: 'app.one', name: '应用一', groups: [
                  { key: 1, name: '广告-开屏', rules: [] },
                  { key: 2, name: '广告-横幅', rules: [] },
                  { key: 3, name: '推荐内容', rules: [] },
                ] },
                { id: 'app.two', name: '应用二', groups: [
                  { key: 1, name: '广告-开屏', rules: [] },
                ] },
              ],
            }
            """
                .trimIndent(),
        )

    @Test
    fun uncategorizedQueryExcludesMatchedGroupsAndAppsWithoutRemainingGroups() {
        val apps = subscription.getCategoryApps(null)
        assertEquals(listOf("app.one"), apps.map { it.id })
        assertEquals(listOf(3), apps.single().groups.map { it.key })
        val preview =
            assertIs<SubscriptionEditResult.Success<RawSubscription>>(
                    CategoryPolicy.previewEdit(subscription, null, "推荐", ""),
                )
                .value
        assertEquals(emptyList<RawSubscription.RawApp>(), preview.getCategoryApps(null))
    }

    @Test
    fun renamingPreviewsCategoryMembershipAndDescription() {
        val preview =
            assertIs<SubscriptionEditResult.Success<RawSubscription>>(
                    CategoryPolicy.previewEdit(subscription, 0, " 推荐 ", "说明"),
                )
                .value
        assertEquals("推荐", preview.categories.first().name)
        assertEquals("说明", preview.categories.first().desc)
        assertEquals(listOf(3), preview.getCategoryApps(0).single().groups.map { it.key })
        assertEquals(2, preview.getCategoryApps(1).sumOf { it.groups.size })
    }

    @Test
    fun addingAnOverlappingPrefixDoesNotStealGroupsFromAnEarlierCategory() {
        val preview =
            assertIs<SubscriptionEditResult.Success<RawSubscription>>(
                    CategoryPolicy.previewEdit(subscription, null, "广告-横幅", ""),
                )
                .value
        val newCategory = preview.categories.single { it.name == "广告-横幅" }
        assertEquals(emptyList<RawSubscription.RawApp>(), preview.getCategoryApps(newCategory.key))
    }

    @Test
    fun staleMissingCategoryAndDuplicateOrBlankNamesCannotProduceAnEdit() {
        assertEquals(
            SubscriptionEditResult.Rejected(SubscriptionFailureReason.CategoryMissing),
            CategoryPolicy.previewEdit(subscription, 99, "新类别", ""),
        )
        assertEquals(
            SubscriptionEditResult.Rejected(SubscriptionFailureReason.CategoryNameDuplicate),
            CategoryPolicy.previewEdit(subscription, 0, " 广告-开屏 ", ""),
        )
        assertEquals(
            SubscriptionEditResult.Rejected(SubscriptionFailureReason.CategoryNameRequired),
            CategoryPolicy.previewEdit(subscription, null, " ", ""),
        )
    }
}
