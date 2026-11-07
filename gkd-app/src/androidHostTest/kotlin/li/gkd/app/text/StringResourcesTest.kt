package li.gkd.app.text

import android.app.Application
import java.util.Locale
import kotlinx.coroutines.runBlocking
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class StringResourcesTest {
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
    fun androidAssetsSelectEnglishChineseAndFallbackWithPluralRules() = runBlocking {
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH)) {
            Locale.setDefault(locale)
            assertEquals("Settings", getString(Res.string.settings_title))
            assertEquals("Settings", Res.string.settings_title.getSync())
            assertEquals("1 minute ago", getPluralString(Res.plurals.time_minutes_ago, 1, "1"))
            assertEquals("2 minutes ago", getPluralString(Res.plurals.time_minutes_ago, 2, "2"))
        }
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        assertEquals("设置", getString(Res.string.settings_title))
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
            Res.string.selector_invalid_detail.getSync(selector, detail),
        )
    }
}
