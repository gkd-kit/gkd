package li.gkd.app.ui

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class StringResourceTest {
    private lateinit var originalLocale: Locale

    @BeforeTest
    fun useChinese() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun changingLocaleUpdatesBlockingSuspendingAndPluralReads() = runBlocking {
        val input = "50% %2\$s <button> & '\${name}'"
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH)) {
            Locale.setDefault(locale)
            assertEquals("Settings", getString(Res.string.settings_title))
            assertEquals("Settings", Res.string.settings_title.getSync())
            assertEquals(
                "A rule named “$input” already exists",
                getString(Res.string.rule_name_duplicate, input),
            )
            assertEquals("1 minute ago", getPluralString(Res.plurals.time_minutes_ago, 1, "1"))
            assertEquals("2 minutes ago", getPluralString(Res.plurals.time_minutes_ago, 2, "2"))
        }
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        assertEquals("设置", getString(Res.string.settings_title))
        assertEquals("2分钟前", getPluralString(Res.plurals.time_minutes_ago, 2, "2"))
    }

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
