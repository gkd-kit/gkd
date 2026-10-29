package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.page.EditorActionsState
import li.gkd.app.ui.state.BaseViewModel

class AppWhitelistViewModel : BaseViewModel() {
    val editorActions = EditorActionsState(scope)

    val initialIds = SettingsRepository.blockMatchAppList.value
    private val editor =
        AppIdListEditor(
            currentIds = { SettingsRepository.blockMatchAppList.value },
            persist = SettingsRepository::saveBlockMatchAppList,
        )
    val text: StateFlow<String>
        field =
            MutableStateFlow(
                SettingsAppIds.format(initialIds, AppInfoRepository.snapshot?.apps.orEmpty()),
            )

    val indicatorSize =
        text
            .debounce(500)
            .map { SettingsAppIds.decode(it).size }
            .stateInit(SettingsAppIds.decode(text.value).size)

    fun setText(value: String) {
        text.value = value
    }

    fun hasChanges(text: String) = editor.hasChanges(text)

    suspend fun saveChanges(text: String): Boolean = editor.save(text)
}
