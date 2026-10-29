package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.ui.state.BaseViewModel

enum class WorkModePrivilegeAction {
    SwitchMode,
    EditScope,
}

class WorkModeViewModel : BaseViewModel() {
    val privilegeAction: StateFlow<WorkModePrivilegeAction?>
        field = MutableStateFlow(null)

    fun setPrivilegeAction(value: WorkModePrivilegeAction?) {
        privilegeAction.value = value
    }
}
