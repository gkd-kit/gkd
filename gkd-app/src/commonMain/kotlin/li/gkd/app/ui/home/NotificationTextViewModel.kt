package li.gkd.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.page.EditorActionsState
import li.gkd.app.ui.state.BaseViewModel

class NotificationTextViewModel : BaseViewModel() {
    val editorActions = EditorActionsState(scope)

    private val initial = SettingsRepository.settings.value

    val titleEnabled: StateFlow<Boolean>
        field = MutableStateFlow(initial.useCustomNotifText)

    fun setTitleEnabled(value: Boolean) {
        titleEnabled.value = value
    }

    val subtitleEnabled: StateFlow<Boolean>
        field = MutableStateFlow(initial.useCustomNotifSubtitle)

    fun setSubtitleEnabled(value: Boolean) {
        subtitleEnabled.value = value
    }

    val title: StateFlow<String>
        field = MutableStateFlow(initial.customNotifTitle)

    fun setTitle(value: String) {
        title.value = value
    }

    val text: StateFlow<String>
        field = MutableStateFlow(initial.customNotifText)

    fun setText(value: String) {
        text.value = value
    }

    fun hasChanges() =
        titleEnabled.value != initial.useCustomNotifText ||
            subtitleEnabled.value != initial.useCustomNotifSubtitle ||
            title.value != initial.customNotifTitle ||
            text.value != initial.customNotifText

    suspend fun save() =
        SettingsRepository.saveNotificationText(
            titleEnabled.value,
            subtitleEnabled.value,
            title.value,
            text.value,
        )
}
