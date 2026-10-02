package li.gkd.app.text

import li.gkd.app.AndroidResourcesTest
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_log_recent_prefix
import li.gkd.app.resources.app_rule_input_hint
import li.gkd.app.resources.notification_summary_template
import li.gkd.app.resources.rule_name_duplicate
import li.gkd.app.resources.selector_invalid_detail
import li.gkd.app.snapshot.SnapshotScreenshotStatus
import li.gkd.app.ui.text.getSync
import org.junit.Assert.assertEquals
import org.junit.Test

class StringResourcesTest : AndroidResourcesTest() {
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

    @Test
    fun androidAssetReadsPreserveWhitespaceAndNewlines() {
        assertEquals("最近触发: ", Res.string.action_log_recent_prefix.getSync())
        assertEquals("请输入应用规则\n", Res.string.app_rule_input_hint.getSync())
    }

    @Test
    fun userSuppliedRuleNamesAreInsertedVerbatimWithoutInterpretingFormatOrXmlCharacters() {
        val name = "50% <button a=\"b\"> & '${'$'}{name}'"
        assertEquals("已存在同名「$name」规则", Res.string.rule_name_duplicate.getSync(name))
    }

    @Test
    fun multilineSelectorErrorsPreserveBothResourceAndArgumentLineBreaks() {
        val selector = "[text=\"a\"]\n[text=\"b\"]"
        val detail = "unexpected %1\$s & <token>"
        assertEquals(
            "非法选择器\n$selector\n$detail",
            Res.string.selector_invalid_detail.getSync(selector, detail)
        )
    }

    @Test
    fun defaultNotificationTemplateRetainsPersistedRuntimePlaceholderSyntax() {
        // These literal tokens are part of the saved custom-notification template format.
        // Resource extraction must not turn them into compile-time interpolation or format arguments.
        assertEquals(
            "\${i}全局/\${k}应用/\${u}规则/\${n}触发",
            Res.string.notification_summary_template.getSync()
        )
    }
}
