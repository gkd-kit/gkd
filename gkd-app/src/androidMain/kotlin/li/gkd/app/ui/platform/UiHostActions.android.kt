package li.gkd.app.ui.platform

import li.gkd.app.platform.service.ServiceController
import li.gkd.app.ui.androidState
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.ui.share.launchUi

actual fun UiHost.setStatusServiceEnabled(enabled: Boolean) {
    if (enabled) mainVm.scope.launchUi { mainVm.androidState.enableStatusService() }
    else ServiceController.setStatusEnabled(false)
}

actual fun UiHost.changeAutomatorMode(mode: AutomatorMode) {
    mainVm.androidState.updateAutomatorMode(mode)
}
