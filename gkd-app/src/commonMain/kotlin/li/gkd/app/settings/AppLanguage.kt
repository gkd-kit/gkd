package li.gkd.app.settings

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The effective resource locale; the user's preference is owned by SettingsStore. */
object AppLanguage {
    val locale: StateFlow<Locale>
        field = MutableStateFlow(Locale.getDefault())

    fun applyLocale(value: Locale) {
        Locale.setDefault(value)
        locale.value = value
    }
}

expect fun applyAppLanguage(languageTag: String?)
