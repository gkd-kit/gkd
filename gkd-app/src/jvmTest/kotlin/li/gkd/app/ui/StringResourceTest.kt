package li.gkd.app.ui

import kotlinx.coroutines.runBlocking
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_log_recent_prefix
import li.gkd.app.resources.app_rule_input_hint
import li.gkd.app.resources.rule_name_duplicate
import li.gkd.app.ui.text.getSync
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class StringResourceTest {
    @Test
    fun blockingAndSuspendingReadsPreserveLiteralUserInput() = runBlocking {
        val input = "50% %2\$s <button> & '\${name}'"
        val expected = "已存在同名「$input」规则"
        assertEquals(expected, getString(Res.string.rule_name_duplicate, input))
        assertEquals(expected, Res.string.rule_name_duplicate.getSync(input))
    }

    @Test
    fun migrationPreservesTrailingWhitespaceAndNewlines() = runBlocking {
        assertEquals("最近触发: ", getString(Res.string.action_log_recent_prefix))
        assertEquals("请输入应用规则\n", getString(Res.string.app_rule_input_hint))
    }
}
