package li.gkd.app.domain.rule

import li.gkd.app.data.RawSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryPolicyTest {
    private val subscription = RawSubscription.parse(
        """
        {
          id: -2, name: 'Local', version: 0,
          categories: [
            { key: 0, name: 'Ads', enable: false },
            { key: 1, name: 'Ads-Splash', enable: true },
          ],
          apps: [
            { id: 'app.one', name: 'App One', groups: [
              { key: 1, name: 'Ads-Splash', rules: [] },
              { key: 2, name: 'Ads-Banner', rules: [] },
              { key: 3, name: 'Recommended Content', rules: [] },
            ] },
            { id: 'app.two', name: 'App Two', groups: [
              { key: 1, name: 'Ads-Splash', rules: [] },
            ] },
          ],
        }
        """.trimIndent(),
    )

    @Test
    fun renamingPreviewsCategoryMembershipAndDescription() {
        val preview = CategoryPolicy.previewEdit(subscription, 0, " Recommend ", "Description")
        assertEquals("Recommend", preview.categories.first().name)
        assertEquals("Description", preview.categories.first().desc)
        assertEquals(listOf(3), preview.getCategoryApps(0).single().groups.map { it.key })
        assertEquals(2, preview.getCategoryApps(1).sumOf { it.groups.size })
    }

    @Test
    fun addingAnOverlappingPrefixDoesNotStealGroupsFromAnEarlierCategory() {
        val preview = CategoryPolicy.previewEdit(subscription, null, "Ads-Banner", "")
        val newCategory = preview.categories.single { it.name == "Ads-Banner" }
        assertEquals(emptyList<RawSubscription.RawApp>(), preview.getCategoryApps(newCategory.key))
    }

    @Test
    fun staleMissingCategoryAndDuplicateOrBlankNamesCannotProduceAnEdit() {
        assertThrows(IllegalStateException::class.java) {
            CategoryPolicy.previewEdit(subscription, 99, "New Category", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryPolicy.previewEdit(subscription, 0, " Ads-Splash ", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryPolicy.previewEdit(subscription, null, " ", "")
        }
    }
}
