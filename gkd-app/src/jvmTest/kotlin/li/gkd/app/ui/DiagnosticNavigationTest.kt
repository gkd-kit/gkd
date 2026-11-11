package li.gkd.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
    fun currentAndLegacyNotificationsReuseRecordEntryAndRemovePagesAboveIt() = runTest {
        val navigator = AppNavigator(this)
        val current = GkdLink.Page(MatchDebugRoute)
        val legacy = GkdLink.parse("gkd://page/1?section=diagnostics") as GkdLink.Page
        navigator.openPage(current)
        runCurrent()
        val records = navigator.topEntry
        for (link in listOf(current, legacy)) {
            navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.Http))
            runCurrent()
            repeat(2) {
                navigator.openPage(link)
                runCurrent()
                assertSame(records, navigator.topEntry)
                assertEquals(
                    listOf(HomeRoute, MatchDebugRoute),
                    navigator.backStack.map { it.route },
                )
                assertNull(records.advancedSectionRequest.value)
            }
        }
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
        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.Event))
        runCurrent()
        assertSame(advanced, navigator.topEntry)
        assertEquals(listOf(HomeRoute, AdvancedPageRoute), navigator.backStack.map { it.route })
        assertEquals(AdvancedSection.Event, advanced.advancedSectionRequest.value)
    }
}
