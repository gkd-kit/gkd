package li.gkd.app.notif

import androidx.core.content.res.ResourcesCompat
import li.gkd.app.AndroidResourcesTest
import li.gkd.app.app
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationIconTest : AndroidResourcesTest() {
    @Test
    fun notificationsResolveHostDrawableThroughManifestMetadata() {
        // Protect the host/core resource contract after removing core's native resources.
        val foreground = ForegroundNotification(ForegroundNotificationKey.Status, "Running")
        val posted = PostedNotification(PostedNotificationKey.SnapshotSaved, "Saved")

        assertEquals(foreground.smallIcon, posted.smallIcon)
        assertEquals("drawable", app.resources.getResourceTypeName(foreground.smallIcon))
        assertEquals("ic_status", app.resources.getResourceEntryName(foreground.smallIcon))
        assertNotNull(ResourcesCompat.getDrawable(app.resources, foreground.smallIcon, null))
    }
}
