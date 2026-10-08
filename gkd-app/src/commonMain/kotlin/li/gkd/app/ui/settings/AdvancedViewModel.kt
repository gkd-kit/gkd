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

    fun setShowEditPortDialog(value: Boolean) {
        showEditPortDialog.value = value
    }

    val showHttpSettingsDialog: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowHttpSettingsDialog(value: Boolean) {
        showHttpSettingsDialog.value = value
    }
}
