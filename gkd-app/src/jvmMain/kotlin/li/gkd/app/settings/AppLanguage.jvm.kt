package li.gkd.app.settings

import java.util.Locale

private var systemLocale = Locale.getDefault()

fun updateSystemLanguage(languageTag: String) {
    systemLocale = Locale.forLanguageTag(languageTag)
    applyAppLanguage(SettingsRepository.settings.value.languageTag)
}

actual fun applyAppLanguage(languageTag: String?) {
    AppLanguage.applyLocale(languageTag?.let(Locale::forLanguageTag) ?: systemLocale)
}
