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
    fun savedNotificationReusesRecordEntryAndRemovesPagesAboveIt() = runTest {
        val navigator = AppNavigator(this)
        val link = GkdLink.Page(MatchDebugRoute)
        navigator.openPage(link)
        runCurrent()
        val records = navigator.topEntry
        navigator.openPage(GkdLink.Page(AdvancedPageRoute, AdvancedSection.Http))
        runCurrent()

        navigator.openPage(link)
        runCurrent()
        assertSame(records, navigator.topEntry)
        assertEquals(listOf(HomeRoute, MatchDebugRoute), navigator.backStack.map { it.route })
        assertNull(records.advancedSectionRequest.value)

        navigator.openPage(link)
        runCurrent()
        assertSame(records, navigator.topEntry)
        assertEquals(2, navigator.backStack.size)
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

    @Test
    fun repeatedLegacyNotificationsReuseAdvancedEntryAndRequestPositioningAgain() = runTest {
        val navigator = AppNavigator(this)
        val link = GkdLink.Page(AdvancedPageRoute, AdvancedSection.Diagnostics)
        navigator.openPage(link)
        runCurrent()
        val advanced = navigator.topEntry
        assertEquals(listOf(HomeRoute, AdvancedPageRoute), navigator.backStack.map { it.route })
        assertEquals(AdvancedSection.Diagnostics, advanced.advancedSectionRequest.value)
        advanced.consumeAdvancedSection()
        assertNull(advanced.advancedSectionRequest.value)

        navigator.openPage(GkdLink.Page(MatchDebugRoute))
        runCurrent()
        assertNull(advanced.advancedSectionRequest.value)
        navigator.openPage(link)
        runCurrent()
        assertSame(advanced, navigator.topEntry)
        assertEquals(listOf(HomeRoute, AdvancedPageRoute), navigator.backStack.map { it.route })
        assertEquals(AdvancedSection.Diagnostics, advanced.advancedSectionRequest.value)

        advanced.consumeAdvancedSection()
        navigator.openPage(link)
        runCurrent()
        assertSame(advanced, navigator.topEntry)
        assertEquals(2, navigator.backStack.size)
        assertEquals(AdvancedSection.Diagnostics, advanced.advancedSectionRequest.value)
    }
}
