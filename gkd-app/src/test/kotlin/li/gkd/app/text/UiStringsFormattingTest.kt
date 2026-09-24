package li.gkd.app.text

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStringsFormattingTest {
    @Test
    fun userSuppliedRuleNamesAreInsertedVerbatimWithoutInterpretingFormatOrXmlCharacters() {
        val name = "50% <button a=\"b\"> & '${'$'}{name}'"
        assertEquals("Rule with name \"$name\" already exists", UiStrings.rule_name_duplicate(name))
    }

    @Test
    fun multilineSelectorErrorsPreserveBothResourceAndArgumentLineBreaks() {
        val selector = "[text=\"a\"]\n[text=\"b\"]"
        val detail = "unexpected %1\$s & <token>"
        assertEquals("Invalid selector\n$selector\n$detail", UiStrings.selector_invalid_detail(selector, detail))
    }

    @Test
    fun defaultNotificationTemplateRetainsPersistedRuntimePlaceholderSyntax() {
        // These literal tokens are part of the saved custom-notification template format.
        // Resource extraction must not turn them into compile-time interpolation or format arguments.
        assertEquals("\${i}Global/\${k}App/\${u}Rule/\${n}Trigger", UiStrings.notification_summary_template)
    }
}
