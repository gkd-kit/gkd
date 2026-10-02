package li.gkd.app.app

import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppInventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppQueryTest {
    private fun app(id: String, name: String = id, system: Boolean = false, user: Int = 0) =
        AppInfo(id, name, 1, "1", system, 0, false, user)

    @Test
    fun nameMatchesPrecedePackageMatchesWithoutDuplicates() {
        val packageOnly = app("chat.package", "Alpha")
        val name = app("example.one", "Chat")
        val both = app("chat.both", "Chat too")
        val result = AppQuery.select(listOf(packageOnly, name, both), "CHAT", 3, 0)
        assertEquals(listOf(name, both, packageOnly), result.apps)
        assertTrue(result.showAllApps) // Search does not turn on the filter indicator.
    }

    @Test
    fun groupAndWhitelistFiltersApplyBeforeSearch() {
        val user = app("user")
        val blocked = app("blocked")
        val system = app("system", system = true)
        val result =
            AppQuery.select(listOf(user, blocked, system), "", 2, 0, false, setOf("blocked"))
        assertEquals(listOf(user), result.apps)
        assertFalse(result.showAllApps)
    }

    @Test
    fun recentActionSortPreservesNameOrderForAppsWithoutHistory() {
        val apps = listOf(app("a"), app("b"), app("c"))
        assertEquals(
            listOf(apps[2], apps[0], apps[1]),
            AppQuery.select(apps, "", 3, 2, actionOrder = listOf("missing", "c")).apps
        )
    }

    @Test
    fun primaryProfileTakesPrecedenceButOtherProfileOnlyAppsRemain() {
        val primary = app("same", user = 0)
        val secondary = app("same", user = 10)
        val otherOnly = app("other", user = 10)
        val inventory =
            AppInventory(apps = listOf(primary), othersApps = listOf(secondary, otherOnly))
        assertEquals(primary, inventory.appsById["same"])
        assertEquals(otherOnly, inventory.appsById["other"])
    }

    @Test
    fun recentVisitsAndUnknownSortPreserveFallbackOrder() {
        val apps = listOf(app("a"), app("b"), app("c"))
        assertEquals(
            listOf(apps[1], apps[0], apps[2]),
            AppQuery.select(
                apps, "", AppGroupFlags.Installed, AppSort.ByUsedTime.value,
                visitOrder = mapOf("b" to 0)
            ).apps
        )
        assertEquals(apps, AppQuery.select(apps, "", AppGroupFlags.Installed, 999).apps)
        assertEquals(
            emptyList(), AppQuery.select(
                apps, "", AppGroupFlags.Uninstalled,
                AppSort.ByAppName.value
            ).apps
        )
    }

}
