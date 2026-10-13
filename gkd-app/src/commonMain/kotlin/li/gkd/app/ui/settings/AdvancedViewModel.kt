package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.ui.state.BaseViewModel

class AdvancedViewModel : BaseViewModel() {
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
