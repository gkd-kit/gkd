package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.page.EditorActionsState
import li.gkd.app.ui.state.BaseViewModel

class AppWhitelistViewModel : BaseViewModel() {
    val editorActions = EditorActionsState(scope)

    val initialIds = SettingsRepository.blockMatchAppList.value
    val text: StateFlow<String>
        field = MutableStateFlow(formatAppIdList(initialIds))

    val indicatorSize = text.debounce(500).map { SettingsAppIds.decode(it).size }
        .stateInit(SettingsAppIds.decode(text.value).size)

    fun setText(value: String) {
        text.value = value
    }

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
