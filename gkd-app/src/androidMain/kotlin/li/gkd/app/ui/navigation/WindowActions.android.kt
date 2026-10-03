package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.resources.Res
import li.gkd.app.resources.recents_lock_manual_hint
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.ToastUtils

@Composable
actual fun AppWindow.privilegeAvailable() =
    privilegeContextFlow.collectAsStateWithLifecycle().value != null

@Composable
actual fun AppWindow.ignoresBatteryOptimizations() =
    PermissionStates.ignoreBatteryOptimizations.stateFlow.collectAsStateWithLifecycle().value

@Composable
actual fun AppWindow.RefreshPermissions() {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    PermissionStates.refreshAll(); delay(1000)
                }
            }
        }
    }
}

actual fun AppWindow.setStatusServiceEnabled(enabled: Boolean) {
    val mainVm = MainViewModel.requireCurrent()
    if (enabled) mainVm.scope.launchUi { mainVm.enableStatusService() }
    else ServiceController.setStatusEnabled(false)
}

actual fun AppWindow.requestIgnoreBatteryOptimizations() {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi { mainVm.permissionRequests.ensurePermissions(PermissionStates.ignoreBatteryOptimizations) }
}

actual fun AppWindow.openAppDetails() {
    IntentUtils.openAppDetailsSettings()
}

actual fun AppWindow.openRecents() {
    val context = privilegeContextFlow.value
    if (context?.keyevent(android.view.KeyEvent.KEYCODE_APP_SWITCH) != true) {
        ToastUtils.show(Res.string.recents_lock_manual_hint.getSync())
    }
}

actual fun AppWindow.openA11ySettings() {
    IntentUtils.openA11ySettings()
}

actual fun AppWindow.changeAutomatorMode(mode: AutomatorModeOption) {
    MainViewModel.requireCurrent().updateAutomatorMode(mode)
}

actual fun AppWindow.switchAutomator() {
    li.gkd.app.service.switchAutomatorService()
}

actual fun AppWindow.requestQueryPackages() {
    val mainVm = MainViewModel.requireCurrent()
    mainVm.scope.launchUi { mainVm.permissionRequests.ensurePermissions(PermissionStates.queryPackages) }
}

actual fun AppWindow.dynamicColorAvailable() = li.gkd.app.util.AndroidTarget.S
