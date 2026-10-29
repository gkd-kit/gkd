package li.gkd.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.AdvancedSection
import li.gkd.app.ui.navigation.GkdLink
import li.gkd.app.ui.navigation.MatchDebugRoute
import li.gkd.app.ui.navigation.SnapshotPageRoute

class GkdLinkTest {
    @Test
    fun existingWebsiteLinksKeepTheirNavigationMeaning() {
        assertEquals(GkdLink.Home(2), GkdLink.parse("gkd://page?tab=%32"))
        assertEquals(GkdLink.Home(null), GkdLink.parse("gkd://page?tab=invalid"))
        assertEquals(GkdLink.Page(AdvancedPageRoute), GkdLink.parse("gkd://page/1"))
        assertEquals(GkdLink.Page(SnapshotPageRoute), GkdLink.parse("gkd://page/2"))
        assertEquals(
            GkdLink.Page(MatchDebugRoute),
            GkdLink.parse("gkd://page/matching-diagnostics"),
        )
        assertEquals(GkdLink.parse("gkd://page/3"), GkdLink.parse("gkd://page/4"))
        assertEquals(GkdLink.WeChatScanner, GkdLink.parse("gkd://invoke/1"))
    }

    @Test
    fun diagnosticNotificationLinkTargetsAdvancedSectionWithoutChangingExistingLinks() {
        val target = GkdLink.Page(AdvancedPageRoute, AdvancedSection.Diagnostics)
        assertEquals(target, GkdLink.parse("gkd://page/1?section=diagnostics"))
        assertEquals(target, GkdLink.parse("gkd://page/1?section=%64iagnostics"))
        assertEquals(
            GkdLink.Page(AdvancedPageRoute),
            GkdLink.parse("gkd://page/1?section=unknown"),
        )
        assertEquals(
            GkdLink.Page(SnapshotPageRoute),
            GkdLink.parse("gkd://page/2?section=diagnostics"),
        )
        assertNull(GkdLink.parse("gkd://page/1?section=%ZZ"))
    }

    @Test
    fun serviceAndTileLinksSelectTheirOwnSettingsItem() {
        // These query values are the external protocol used by notifications and manifest tiles.
        val targets =
            mapOf(
                "http" to AdvancedSection.Http,
                "snapshot-button" to AdvancedSection.SnapshotButton,
                "snapshot-settings" to AdvancedSection.SnapshotSettings,
                "activity" to AdvancedSection.Activity,
                "event" to AdvancedSection.Event,
                "track" to AdvancedSection.Track,
            )
        targets.forEach { (value, section) ->
            assertEquals(
                GkdLink.Page(AdvancedPageRoute, section),
                GkdLink.parse("gkd://page/1?section=$value"),
            )
        }
    }

    @Test
    fun malformedAndUnknownLinksDoNotTriggerActions() {
        for (value in
            listOf(
                "https://page/1",
                "gkd://page/100",
                "gkd://invoke/2",
                "gkd://page?tab=%ZZ",
            )) {
            assertNull(GkdLink.parse(value), value)
        }
    }
}
