package li.gkd.app.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppScopePolicyTest {
    @Test
    fun partialCloseStillBlocksMatchingInBothEngineModes() {
        for (mode in AutomatorMode.entries) {
            val settings =
                SettingsStore(
                    automatorMode = mode.value,
                    enableBlockA11yAppList = true,
                    blockA11yAppListFollowMatch = false,
                )
            val match = setOf("blocked.match")
            val a11y = setOf("blocked.partial")
            assertTrue(AppScopePolicy.blocksMatch(settings, match, a11y, "blocked.partial"))
            assertFalse(
                AppScopePolicy.blocksMatch(
                    settings.copy(enableBlockA11yAppList = false),
                    match,
                    a11y,
                    "blocked.partial",
                ),
            )
            assertTrue(AppScopePolicy.blocksMatch(settings, match, a11y, "blocked.match"))
        }
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
