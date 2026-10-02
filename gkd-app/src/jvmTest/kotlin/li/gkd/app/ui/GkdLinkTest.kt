package li.gkd.app.ui

import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.GkdLink
import li.gkd.app.ui.navigation.SnapshotPageRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GkdLinkTest {
    @Test
    fun existingWebsiteLinksKeepTheirNavigationMeaning() {
        assertEquals(GkdLink.Home(2), GkdLink.parse("gkd://page?tab=%32"))
        assertEquals(GkdLink.Home(null), GkdLink.parse("gkd://page?tab=invalid"))
        assertEquals(GkdLink.Page(AdvancedPageRoute), GkdLink.parse("gkd://page/1"))
        assertEquals(GkdLink.Page(SnapshotPageRoute), GkdLink.parse("gkd://page/2"))
        assertEquals(GkdLink.parse("gkd://page/3"), GkdLink.parse("gkd://page/4"))
        assertEquals(GkdLink.WeChatScanner, GkdLink.parse("gkd://invoke/1"))
    }

    @Test
    fun malformedAndUnknownLinksDoNotTriggerActions() {
        for (value in listOf(
            "https://page/1",
            "gkd://page/100",
            "gkd://invoke/2",
            "gkd://page?tab=%ZZ"
        )) {
            assertNull(GkdLink.parse(value), value)
        }
    }
}
