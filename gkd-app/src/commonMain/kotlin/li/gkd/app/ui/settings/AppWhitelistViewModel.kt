package li.gkd.app.ui.settings

import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.state.BaseViewModel

class AppWhitelistViewModel : BaseViewModel() {
    val initialIds = SettingsRepository.blockMatchAppList.value
    private var savedIds = initialIds
    fun hasChanges(text: String) = SettingsAppIds.decode(text) != savedIds
    suspend fun saveChanges(text: String): Boolean {
        val changed = hasChanges(text)
        // Re-submit unchanged values too: a previous failed write must be retryable.
        val ids = SettingsAppIds.decode(text)
        SettingsRepository.replaceBlockMatchAppList(ids)
        SettingsRepository.awaitPersistence()
        savedIds = ids
        return changed
    }
}
