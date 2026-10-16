package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.state.BaseViewModel
import org.jetbrains.compose.resources.getString

class AdvancedViewModel : BaseViewModel() {
    fun requestStatusOverlayEnabled(enabled: Boolean) {
        scope.launch {
            val confirmed = MainViewModel.requireCurrent().dialogRequests.confirm(
                title = getString(
                    if (enabled) Res.string.status_overlay_enable_title
                    else Res.string.status_overlay_disable_title
                ),
                text = getString(
                    if (enabled) Res.string.status_overlay_enable_description
                    else Res.string.status_overlay_disable_description
                ),
                confirmText = getString(
                    if (enabled) Res.string.action_turn_on else Res.string.action_close
                ),
                dismissOnRequest = true,
            )
            if (confirmed) {
                SettingsRepository.updateSettings { it.copy(enableStatusOverlay = enabled) }
            }
        }
    }

    val showTrackNotice: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowTrackNotice(value: Boolean) {
        showTrackNotice.value = value
    }

    val showEditPortDialog: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val portDraft: StateFlow<String>
        field = MutableStateFlow("")

    fun editPort(port: Int) {
        portDraft.value = port.toString()
        showEditPortDialog.value = true
    }

    fun setPortDraft(value: String) {
        portDraft.value = value.filter(Char::isDigit).take(5)
    }

    fun closeEditPortDialog() {
        showEditPortDialog.value = false
    }

    val showHttpSettingsDialog: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowHttpSettingsDialog(value: Boolean) {
        showHttpSettingsDialog.value = value
    }
}
