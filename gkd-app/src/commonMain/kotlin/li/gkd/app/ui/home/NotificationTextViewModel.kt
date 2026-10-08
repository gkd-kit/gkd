package li.gkd.app.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.state.BaseViewModel

class NotificationTextViewModel : BaseViewModel() {
    private val initial = SettingsRepository.settings.value

    val enabled: StateFlow<Boolean>
        field = MutableStateFlow(initial.useCustomNotifText)

    fun setEnabled(value: Boolean) {
        enabled.value = value
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
        enabled.value != initial.useCustomNotifText ||
            title.value != initial.customNotifTitle ||
            text.value != initial.customNotifText

    suspend fun save() = SettingsRepository.saveNotificationText(enabled.value, title.value, text.value)
}
