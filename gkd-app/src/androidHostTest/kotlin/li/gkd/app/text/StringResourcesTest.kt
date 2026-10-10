package li.gkd.app.text

import android.app.Application
import li.gkd.app.resources.*
import li.gkd.app.ui.text.getSync
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class StringResourcesTest {
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
