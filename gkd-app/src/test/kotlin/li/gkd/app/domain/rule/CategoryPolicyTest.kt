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
        """.trimIndent(),
    )

    @Test
    fun renamingPreviewsCategoryMembershipAndDescription() {
        val preview = CategoryPolicy.previewEdit(subscription, 0, " 推荐 ", "说明")
        assertEquals("推荐", preview.categories.first().name)
        assertEquals("说明", preview.categories.first().desc)
        assertEquals(listOf(3), preview.getCategoryApps(0).single().groups.map { it.key })
        assertEquals(2, preview.getCategoryApps(1).sumOf { it.groups.size })
    }

    @Test
    fun addingAnOverlappingPrefixDoesNotStealGroupsFromAnEarlierCategory() {
        val preview = CategoryPolicy.previewEdit(subscription, null, "广告-横幅", "")
        val newCategory = preview.categories.single { it.name == "广告-横幅" }
        assertEquals(emptyList<RawSubscription.RawApp>(), preview.getCategoryApps(newCategory.key))
    }

    @Test
    fun staleMissingCategoryAndDuplicateOrBlankNamesCannotProduceAnEdit() {
        assertThrows(IllegalStateException::class.java) {
            CategoryPolicy.previewEdit(subscription, 99, "新类别", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryPolicy.previewEdit(subscription, 0, " 广告-开屏 ", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryPolicy.previewEdit(subscription, null, " ", "")
        }
    }
}
