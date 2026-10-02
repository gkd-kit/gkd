package li.gkd.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppScopePolicyTest {
    @Test
    fun matchingHonorsIndependentAndFollowedAccessibilityBlocks() {
        val settings =
            SettingsStore(enableBlockA11yAppList = true, blockA11yAppListFollowMatch = false)
        val match = setOf("blocked.match")
        val a11y = setOf("blocked.a11y")
        assertTrue(AppScopePolicy.blocksMatch(settings, match, a11y, "blocked.match"))
        assertTrue(AppScopePolicy.blocksMatch(settings, match, a11y, "blocked.a11y"))
        assertFalse(AppScopePolicy.blocksMatch(settings, match, a11y, "allowed"))
        assertFalse(
            AppScopePolicy.blocksMatch(
                settings.copy(blockA11yAppListFollowMatch = true),
                match,
                a11y,
                "blocked.a11y"
            )
        )
        assertFalse(
            AppScopePolicy.blocksMatch(
                settings.copy(enableBlockA11yAppList = false),
                match,
                a11y,
                "blocked.a11y"
            )
        )
        assertTrue(
            AppScopePolicy.blocksMatch(
                settings.copy(enableBlockA11yAppList = false),
                match,
                a11y,
                "blocked.match"
            )
        )
    }

    @Test
    fun disablingAutomationSuppressesScopeWithoutChangingSavedSelection() {
        val scope = setOf("app.one")
        assertEquals(
            emptySet(),
            AppScopePolicy.a11yScope(SettingsStore(automatorMode = AutomatorMode.A11y.value), scope)
        )
        assertEquals(
            scope,
            AppScopePolicy.a11yScope(
                SettingsStore(automatorMode = AutomatorMode.Automation.value),
                scope
            )
        )
    }
}
