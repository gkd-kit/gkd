package li.songe.gkd.util

import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import li.songe.gkd.App
import java.util.*

class LocaleString(private vararg val localeList: Locale) : (Int) -> String {
    private val languageContext by lazy {
        App.context.createConfigurationContext(Configuration(App.context.resources.configuration).apply {
            if (this@LocaleString.localeList.isNotEmpty()) {
                setLocales(LocaleList(*this@LocaleString.localeList))
            } else {
                setLocales(App.context.resources.configuration.locales)
            }
        })
    }

    override fun invoke(@StringRes resId: Int) = languageContext.getString(resId)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LocaleString
        if (!localeList.contentEquals(other.localeList)) return false
        return true
    }

    override fun hashCode(): Int {

        return localeList.contentHashCode()
    }

    companion object {
        var localeString by mutableStateOf(LocaleString())
    }

}