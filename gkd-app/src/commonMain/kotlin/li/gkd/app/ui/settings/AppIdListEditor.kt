package li.gkd.app.ui.settings

import li.gkd.app.app.AppInfoRepository
import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.settings.SettingsRepository

/** The draft belongs to Compose; this object only prepares and persists explicit edits. */
class AppIdListEditor(
    private val currentIds: () -> Set<String>,
    private val replace: (Set<String>) -> Unit,
) {
    fun initialText(): String {
        val appNames = AppInfoRepository.snapshot?.apps.orEmpty().mapValues { it.value.name }
        return currentIds().sorted().sortedBy { if (it in appNames) 0 else 1 }
            .joinToString("\n\n", postfix = "\n\n") { id ->
                appNames[id]?.let { "$id\n# $it" } ?: id
            }
    }

    fun hasChanges(text: String) = currentIds() != SettingsAppIds.decode(text)

    suspend fun save(text: String): Boolean {
        val ids = SettingsAppIds.decode(text)
        val changed = currentIds() != ids
        replace(ids)
        SettingsRepository.awaitPersistence()
        return changed
    }
}
