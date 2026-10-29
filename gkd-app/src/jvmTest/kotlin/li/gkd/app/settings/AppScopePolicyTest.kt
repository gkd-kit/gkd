package li.gkd.app.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppScopePolicyTest {
    @Test
    fun modeExceptionsAreIndependentAndNewAppsKeepTheDefaultMode() {
        val a11y = setOf("app.a11y", "app.both")
        val automation = setOf("app.automation", "app.both")
        val defaults = SettingsStore()
        assertFalse(AppScopePolicy.usesA11y(defaults, a11y, automation, "app.automation"))
        assertFalse(AppScopePolicy.usesA11y(defaults, a11y, automation, "app.both"))
        assertTrue(AppScopePolicy.usesA11y(defaults, a11y, automation, "app.new"))
        val switched = defaults.copy(automatorMode = AutomatorMode.Automation.value)
        assertTrue(AppScopePolicy.usesA11y(switched, a11y, automation, "app.a11y"))
        assertTrue(AppScopePolicy.usesA11y(switched, a11y, automation, "app.both"))
        assertFalse(AppScopePolicy.usesA11y(switched, a11y, automation, "app.new"))
    }

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
                "blocked.a11y",
            ),
        )
        assertFalse(
            AppScopePolicy.blocksMatch(
                settings.copy(enableBlockA11yAppList = false),
                match,
                a11y,
                "blocked.a11y",
            ),
        )
        assertTrue(
            AppScopePolicy.blocksMatch(
                settings.copy(enableBlockA11yAppList = false),
                match,
                a11y,
                "blocked.match",
            ),
        )
    }
}
