package li.gkd.app.notif

import android.app.Application
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class NotificationTextTest {
    @Test
    fun exportFailureNotificationStillSaysSnapshotSaved() {
        val notification = NotificationCatalog.snapshotSaved(
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
        val notification = NotificationCatalog.snapshotSaved(
            appName = name,
            activityId = "example.MainActivity",
            screenshotStatus = SnapshotScreenshotStatus.Captured,
            savedToDownloads = true,
        )
        assertEquals("快照已保存 · $name", notification.title)
        assertEquals("example.MainActivity · 已保存至下载", notification.text)
    }
}
