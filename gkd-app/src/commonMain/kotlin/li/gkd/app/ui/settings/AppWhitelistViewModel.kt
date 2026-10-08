package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.settings.SettingsAppIds
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.state.BaseViewModel

class AppWhitelistViewModel : BaseViewModel() {
    val initialIds = SettingsRepository.blockMatchAppList.value
    val text: StateFlow<String>
        field = MutableStateFlow(run {
            val names = AppInfoRepository.snapshot?.apps.orEmpty()
            initialIds.sorted().sortedBy { if (it in names) 0 else 1 }
                .joinToString("\n\n", postfix = "\n\n") { id ->
                    names[id]?.let { "$id\n# ${it.name}" } ?: id
                }
        })

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
