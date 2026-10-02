package li.gkd.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.privilegeUiState
import li.gkd.app.ui.component.LocalEditorWindowInsets
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.page.GkPrivilegeTopBar
import li.gkd.app.ui.page.PrivilegeServiceInfoDialog
import priv.kit.ui.PrivilegeScreen

@Composable
actual fun PrivilegeServicePage(window: AppWindow) {
    val state = window.state
    val session = window

    var showInfo by rememberSaveable { mutableStateOf(false) }
    val settings by state.simulator.settings.collectAsStateWithLifecycle()
    val device = settings.environment().android
    val simulation = state.privilege
    if (showInfo) PrivilegeServiceInfoDialog { showInfo = false }
    if (settings.privilege.externalAuthorizationRequested) {
        AlertDialog(
            onDismissRequest = simulation::cancelOperation,
            title = { Text("模拟外部授权") },
            text = { Text("允许模拟 Shizuku 启动特权服务？") },
            confirmButton = {
                TextButton(onClick = simulation::confirmExternalAuthorization) {
                    Text(
                        "允许"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = simulation::cancelOperation) {
                    Text(
                        "取消"
                    )
                }
            },
        )
    }
    PrivilegeScreen(
        state = settings.privilegeUiState(),
        actions = simulation.actions, interactionEnabled = true, showFeedback = state.toast::show,
        onViewPermissionSolutions = null,
        systemPromptWindowInsets = WindowInsets(top = device.topInset.dp),
        contentWindowInsets = LocalEditorWindowInsets.current
            ?: ScaffoldDefaults.contentWindowInsets,
        topBar = { GkPrivilegeTopBar(state::popPage) { showInfo = true } },
    )
}
