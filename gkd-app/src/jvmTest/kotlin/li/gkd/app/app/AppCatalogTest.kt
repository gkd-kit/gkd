package li.gkd.app.app

import li.gkd.app.model.AppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppCatalogTest {
    @Test
    fun activityLabelsOnlyShortenAnExactPackagePrefix() {
        assertEquals(".Main", ActivityNames.getShowActivityId("app.one", "app.one.Main"))
        assertEquals("app.other.Main", ActivityNames.getShowActivityId("app.one", "app.other.Main"))
        assertEquals("app.ones.Main", ActivityNames.getShowActivityId("app.one", "app.ones.Main"))
        assertEquals("app.one", ActivityNames.getShowActivityId("app.one", "app.one"))
        assertNull(ActivityNames.getShowActivityId("app.one", null))
    }

    @Test
    fun visibleAppsFiltersHiddenAppsAndSortsByName() {
        fun app(id: String, name: String, hidden: Boolean = false) =
            AppInfo(id, name, 1, "1", false, 0, hidden, 0)

        val apps =
            listOf(app("b", "Beta"), app("a", "Alpha"), app("hidden", "Hidden", hidden = true))
        val visible = AppCatalog.visibleApps(apps)
        assertEquals(listOf("a", "b"), visible.map { it.id })
    }
}
