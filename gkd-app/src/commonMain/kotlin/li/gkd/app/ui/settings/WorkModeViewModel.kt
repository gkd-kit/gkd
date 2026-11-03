package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.platform.openA11ySettings
import li.gkd.app.ui.state.BaseViewModel
import org.jetbrains.compose.resources.getString

enum class WorkModePrivilegeAction {
    SwitchMode,
}

class WorkModeViewModel : BaseViewModel() {
    fun requestA11yEnable() {
        scope.launch {
            val confirmed =
                MainViewModel.requireCurrent()
                    .dialogRequests
                    .confirm(
                        title = getString(Res.string.a11y_enable_guide_title),
                        text = getString(Res.string.a11y_enable_guide_description),
                        confirmText = getString(Res.string.action_go),
                        dismissOnRequest = true,
                    )
            if (confirmed) openA11ySettings()
        }
    }

    val privilegeAction: StateFlow<WorkModePrivilegeAction?>
        field = MutableStateFlow(null)

    fun setPrivilegeAction(value: WorkModePrivilegeAction?) {
        privilegeAction.value = value
    }
}
