package li.gkd.app.text

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStringsFormattingTest {
    @Test
    fun userSuppliedRuleNamesAreInsertedVerbatimWithoutInterpretingFormatOrXmlCharacters() {
        val name = "50% <button a=\"b\"> & '${'$'}{name}'"
        assertEquals("已存在同名「$name」规则", UiStrings.rule_name_duplicate(name))
    }

    @Test
    fun multilineSelectorErrorsPreserveBothResourceAndArgumentLineBreaks() {
        val selector = "[text=\"a\"]\n[text=\"b\"]"
        val detail = "unexpected %1\$s & <token>"
        assertEquals("非法选择器\n$selector\n$detail", UiStrings.selector_invalid_detail(selector, detail))
    }

    @Test
    fun defaultNotificationTemplateRetainsPersistedRuntimePlaceholderSyntax() {
        // These literal tokens are part of the saved custom-notification template format.
        // Resource extraction must not turn them into compile-time interpolation or format arguments.
        assertEquals("\${i}全局/\${k}应用/\${u}规则/\${n}触发", UiStrings.notification_summary_template)
    }
}
