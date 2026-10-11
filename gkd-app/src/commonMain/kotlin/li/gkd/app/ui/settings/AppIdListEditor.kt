package li.gkd.app.ui.settings

import li.gkd.app.app.AppInfoRepository
import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.settings.SettingsRepository

fun formatAppIdList(ids: Set<String>): String {
    val apps = AppInfoRepository.snapshot?.apps.orEmpty()
    return ids.sorted().sortedBy { if (it in apps) 0 else 1 }
        .joinToString("\n\n", postfix = "\n\n") { id ->
            apps[id]?.let { "$id\n# ${it.name}" } ?: id
        }
}

/** Prepares and persists explicit app ID edits; the owning page ViewModel manages the draft. */
class AppIdListEditor(
    private val currentIds: () -> Set<String>,
    private val replace: (Set<String>) -> Unit,
) {
    fun initialText(): String = formatAppIdList(currentIds())

    fun hasChanges(text: String) = currentIds() != SettingsAppIds.decode(text)

    suspend fun save(text: String): Boolean {
        val ids = SettingsAppIds.decode(text)
        val changed = currentIds() != ids
        replace(ids)
        SettingsRepository.awaitPersistence()
        return changed
    }
}
