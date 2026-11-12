package li.gkd.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import li.gkd.app.ui.home.BottomNavItem
import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.AdvancedSection
import li.gkd.app.ui.navigation.AppNavigator
import li.gkd.app.ui.navigation.GkdLink
import li.gkd.app.ui.navigation.HomeRoute
import li.gkd.app.ui.navigation.MatchDebugRoute
import li.gkd.app.ui.navigation.SnapshotSettingsRoute

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticNavigationTest {
    @Test
    fun currentAndLegacyNotificationsOpenDiagnosticsWithToolsAndSettingsBehindIt() = runTest {
        for (uri in listOf("gkd://page/matching-diagnostics", "gkd://page/1?section=diagnostics")) {
            val navigator = AppNavigator(this)
            val link = GkdLink.parse(uri) as GkdLink.Page
            navigator.openPage(link)
            runCurrent()
            val records = navigator.topEntry
            assertEquals(BottomNavItem.Settings, navigator.selectedHomeTab)
            navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.SnapshotButton))
            runCurrent()
            repeat(2) {
                navigator.openPage(link)
                runCurrent()
                assertSame(records, navigator.topEntry)
                assertEquals(
                    listOf(HomeRoute, AdvancedPageRoute, MatchDebugRoute),
                    navigator.backStack.map { it.route },
                )
                assertNull(records.advancedSectionRequest.value)
            }
        }
    }

    @Test
    fun notificationPreservesPreviouslyOpenPageBelowTools() = runTest {
        val navigator = AppNavigator(this)
        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.SnapshotButton))
        runCurrent()
        val previous = navigator.topEntry

        navigator.openPage(GkdLink.Page(MatchDebugRoute))
        runCurrent()
        assertEquals(
            listOf(HomeRoute, SnapshotSettingsRoute, AdvancedPageRoute, MatchDebugRoute),
            navigator.backStack.map { it.route },
        )
        assertSame(previous, navigator.backStack[1])
        assertEquals(BottomNavItem.Dashboard, navigator.selectedHomeTab)
    }

    @Test
    fun snapshotButtonNotificationTargetsItsSettingsAndOtherShortcutsReuseAdvanced() = runTest {
        val navigator = AppNavigator(this)
        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.Http))
        runCurrent()
        val advanced = navigator.topEntry

        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.SnapshotButton))
        runCurrent()
        val snapshot = navigator.topEntry
        assertNotSame(advanced, snapshot)
        assertEquals(SnapshotSettingsRoute, snapshot.route)
        assertEquals(AdvancedSection.SnapshotButton, snapshot.advancedSectionRequest.value)
        snapshot.consumeAdvancedSection()

        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.SnapshotButton))
        runCurrent()
        assertSame(snapshot, navigator.topEntry)
        assertEquals(AdvancedSection.SnapshotButton, snapshot.advancedSectionRequest.value)

        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.SnapshotSettings))
        runCurrent()
        assertSame(advanced, navigator.topEntry)
        assertEquals(AdvancedSection.SnapshotSettings, advanced.advancedSectionRequest.value)

        navigator.openPage(GkdLink.Page(MatchDebugRoute))
        runCurrent()
        assertEquals(
            listOf(HomeRoute, AdvancedPageRoute, MatchDebugRoute),
            navigator.backStack.map { it.route },
        )
        assertSame(advanced, navigator.backStack[1])
        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.Event))
        runCurrent()
        assertSame(advanced, navigator.topEntry)
        assertEquals(listOf(HomeRoute, AdvancedPageRoute), navigator.backStack.map { it.route })
        assertEquals(AdvancedSection.Event, advanced.advancedSectionRequest.value)
    }
}
