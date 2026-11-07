package li.gkd.app.settings

import android.app.LocaleManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.notif.NotificationChannels
import li.gkd.app.notif.NotificationDispatcher
import priv.kit.ui.PrivilegeUi

/** System settings can change while the application process is stopped. */
fun syncSystemLanguage() {
    if (Build.VERSION.SDK_INT >= 33) {
        val locales = app.getSystemService(LocaleManager::class.java).applicationLocales
        val languageTag = if (locales.isEmpty) null else locales[0].language
        // This is installation metadata, not a preference: it must not travel with backups.
        val migrationMarker = app.noBackupFilesDir.resolve("app-language-synced")
        if (!migrationMarker.exists()) {
            val storedLanguage = SettingsRepository.settings.value.languageTag
            if (languageTag == null && storedLanguage != null) {
                applyAppLanguage(storedLanguage)
                migrationMarker.createNewFile()
                return
            }
            migrationMarker.createNewFile()
        }
        if (SettingsRepository.settings.value.languageTag != languageTag) {
            SettingsRepository.updateSettings { it.copy(languageTag = languageTag) }
            return
        }
    }
    applyAppLanguage(SettingsRepository.settings.value.languageTag)
}

actual fun applyAppLanguage(languageTag: String?) {
    appScope.launch(Dispatchers.Main.immediate) {
        val locale =
            if (languageTag != null) {
                Locale.forLanguageTag(languageTag)
            } else {
                val systemLocales = Resources.getSystem().configuration.locales
                (0 until systemLocales.size())
                    .asSequence()
                    .map { systemLocales[it] }
                    .firstOrNull { it.language == "zh" || it.language == "en" } ?: Locale.ENGLISH
            }
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = app.getSystemService(LocaleManager::class.java)
            val current = manager.applicationLocales
            val currentTag = if (current.isEmpty) null else current[0].language
            if (currentTag != languageTag) {
                manager.applicationLocales = LocaleList.forLanguageTags(languageTag.orEmpty())
            }
        } else {
            val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(config, app.resources.displayMetrics)
        }
        val changed = AppLanguage.locale.value != locale
        AppLanguage.applyLocale(locale)
        PrivilegeUi.setLocale(locale)
        if (changed) {
            NotificationChannels.initialize()
            NotificationDispatcher.refreshForegroundLanguages()
        }
    }
}
