package li.gkd.app.settings

import kotlinx.serialization.json.Json
import li.gkd.app.app.AppQuery
import li.gkd.app.model.AppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsCompatibilityTest {
    @Test
    fun persistedNumericSettingsStillDriveModeFilteringAndSort() {
        // These literal values protect existing store.json files, not enum declarations.
        val store = Json.decodeFromString<SettingsStore>(
            """{"automatorMode":2,"appGroupType":2,"appSort":3}"""
        )
        val first = AppInfo("a", "A", 1, "1", false, 0, false, 0)
        val recent = first.copy(id = "b", name = "B")
        val system = first.copy(id = "system", isSystem = true)
        assertTrue(store.useAutomation)
        assertFalse(store.useA11y)
        assertEquals(
            listOf(recent, first), AppQuery.select(
                listOf(system, first, recent), "",
                store.appGroupType, store.appSort, visitOrder = mapOf("b" to 0)
            ).apps
        )
    }

    @Test
    fun missingFieldsKeepLegacyDefaultsAndInheritedGroups() {
        val defaults = Json.decodeFromString<SettingsStore>("{}")
        assertTrue(defaults.useA11y)
        assertFalse(defaults.useAutomation)
        assertEquals(86_400_000L, defaults.updateSubsInterval)
        assertEquals(3, defaults.appSort)
        assertEquals(3, defaults.appGroupType)
        assertEquals(0, defaults.appRuleSort)
        assertEquals(0, defaults.updateChannel)
        assertEquals(1, defaults.snapshotDisplayMode)
        val inherited = Json.decodeFromString<SettingsStore>("""{"appGroupType":7}""")
        assertEquals(7, inherited.a11yScopeAppGroupType)
        assertEquals(7, inherited.subsExcludeAppGroupType)
    }
}
