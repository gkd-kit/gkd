package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.ui.state.BaseViewModel

class WorkModeViewModel : BaseViewModel() {
    val showPrivilegeRequired: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowPrivilegeRequired(value: Boolean) {
        showPrivilegeRequired.value = value
    }

    val showKeepAlive: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowKeepAlive(value: Boolean) {
        showKeepAlive.value = value
    }
}
