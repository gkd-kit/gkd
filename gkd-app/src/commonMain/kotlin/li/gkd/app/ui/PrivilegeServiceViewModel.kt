package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.ui.state.BaseViewModel

class PrivilegeServiceViewModel : BaseViewModel() {
    val showInfo: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowInfo(value: Boolean) {
        showInfo.value = value
    }
}
