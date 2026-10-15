package li.gkd.app.settings

import kotlinx.serialization.json.Json
import li.gkd.app.app.AppQuery
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleDisplayMode
import li.gkd.app.snapshot.SnapshotDisplayMode
import li.gkd.app.subscription.SubscriptionJson.json
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
        assertEquals(0, defaults.appRuleSummaryDisplayMode)
        assertEquals(0, defaults.subsAppRuleDisplayMode)
        assertEquals(0, defaults.updateChannel)
        assertEquals(1, defaults.snapshotDisplayMode)
        val inherited = Json.decodeFromString<SettingsStore>("""{"appGroupType":7}""")
        assertEquals(7, inherited.a11yScopeAppGroupType)
        assertEquals(7, inherited.subsExcludeAppGroupType)
    }

    @Test
    fun discardedRuleDisplayFieldDoesNotSetEitherPagePreference() {
        val store = json.decodeFromString<SettingsStore>(
            """{"appRuleSort":2,"appRuleDisplayMode":1}"""
        )
        assertEquals(0, store.appRuleSummaryDisplayMode)
        assertEquals(0, store.subsAppRuleDisplayMode)
        assertEquals(2, store.appRuleSort)
        assertFalse(json.encodeToString(store).contains("\"appRuleDisplayMode\""))
    }

    @Test
    fun savedRuleDisplayPreferencesRemainIndependent() {
        val store = json.decodeFromString<SettingsStore>(
            """{"appRuleSummaryDisplayMode":1,"subsAppRuleDisplayMode":0,"appRuleSort":2}"""
        )
        assertEquals(1, store.appRuleSummaryDisplayMode)
        assertEquals(0, store.subsAppRuleDisplayMode)
        val updated = store.copy(subsAppRuleDisplayMode = 1)
        assertEquals(1, updated.appRuleSummaryDisplayMode)
        assertEquals(updated, json.decodeFromString<SettingsStore>(json.encodeToString(updated)))
    }

    @Test
    fun persistedDisplayModesSwitchWithoutChangingNumericContract() {
        // Literal values protect the existing store.json representation.
        val saved = json.decodeFromString<SettingsStore>(
            """{"appRuleSummaryDisplayMode":1,"subsAppRuleDisplayMode":0,"snapshotDisplayMode":2}"""
        )
        fun switchModes(store: SettingsStore) = store.copy(
            appRuleSummaryDisplayMode = RuleDisplayMode.switch(store.appRuleSummaryDisplayMode),
            subsAppRuleDisplayMode = RuleDisplayMode.switch(store.subsAppRuleDisplayMode),
            snapshotDisplayMode = SnapshotDisplayMode.switch(store.snapshotDisplayMode),
        )
        val switched = json.decodeFromString<SettingsStore>(json.encodeToString(switchModes(saved)))
        assertEquals(0, switched.appRuleSummaryDisplayMode)
        assertEquals(1, switched.subsAppRuleDisplayMode)
        assertEquals(1, switched.snapshotDisplayMode)
        assertEquals(saved, switchModes(switched))
    }

    @Test
    fun unknownPersistedDisplayModesKeepExistingSwitchFallbacks() {
        val saved = json.decodeFromString<SettingsStore>(
            """{"appRuleSummaryDisplayMode":99,"snapshotDisplayMode":99}"""
        )
        assertEquals(1, RuleDisplayMode.switch(saved.appRuleSummaryDisplayMode))
        assertEquals(2, SnapshotDisplayMode.switch(saved.snapshotDisplayMode))
    }
}
