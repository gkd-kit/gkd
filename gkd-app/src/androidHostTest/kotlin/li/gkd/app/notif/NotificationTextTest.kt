package li.gkd.app.notif

import android.app.Application
import java.util.Locale
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class NotificationTextTest {
    private lateinit var originalLocale: Locale

    @Before
    fun useChinese() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun existingForegroundSnapshotResolvesNewLanguageWithoutLosingRuntimeContent() {
        // A running service keeps the latest notification snapshot across language changes.
        val button = NotificationCatalog.button()
        val debug = NotificationCatalog.debug(8888, listOf("192.168.1.2"))
        val activity = NotificationCatalog.activity("example.MainActivity")
        val unknownActivity = NotificationCatalog.activity()
        val saving = NotificationCatalog.matchDiagnostic(saving = true)
        assertEquals("快照按钮已开启", button.title)
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Snapshot button enabled", button.title)
        assertEquals("Debug service enabled", debug.title)
        assertEquals("192.168.1.2:8888", debug.text)
        assertEquals("example.MainActivity", activity.text)
        assertEquals("Unknown", unknownActivity.text)
        assertEquals(null, saving.text)
        assertEquals(null, saving.stopService)
    }

    @Test
    fun exportFailureNotificationStillSaysSnapshotSaved() {
        val notification =
            NotificationCatalog.snapshotSaved(
                appName = "Example",
                activityId = null,
                screenshotStatus = SnapshotScreenshotStatus.Captured,
                savedToDownloads = false,
                exportDetail = "自动导出失败：disk full",
            )
        assertEquals("快照已保存 · Example", notification.title)
        assertEquals("自动导出失败：disk full", notification.text)
    }

    @Test
    fun snapshotNotificationPreservesAppNameAndDownloadResult() {
        val name = "50% %1\$s <App>"
        val notification =
            NotificationCatalog.snapshotSaved(
                appName = name,
                activityId = "example.MainActivity",
                screenshotStatus = SnapshotScreenshotStatus.Captured,
                savedToDownloads = true,
            )
        assertEquals("快照已保存 · $name", notification.title)
        assertEquals("example.MainActivity · 已保存至下载", notification.text)
    }
}
