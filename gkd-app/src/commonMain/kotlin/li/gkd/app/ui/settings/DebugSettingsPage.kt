package li.gkd.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.stringResource

@Composable
fun DebugSettingsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { DebugSettingsViewModel() }
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val showEditPortDialog by vm.showEditPortDialog.collectAsStateWithLifecycle()
    val portDraft by vm.portDraft.collectAsStateWithLifecycle()
    if (showEditPortDialog) {
        EditHttpPortDialog(
            value = portDraft,
            onValueChange = vm::setPortDraft,
            onDismissRequest = { vm.closeEditPortDialog() },
            onConfirm = { text ->
                val oldPort = SettingsRepository.settings.value.httpServerPort
                if (SettingsRepository.saveHttpServerPort(text)) {
                    if (oldPort != text.toInt()) {
                        httpServerPortChanged()
                        ToastUtils.show(Res.string.update_success.getSync())
                    }
                    vm.closeEditPortDialog()
                } else {
                    ToastUtils.show(Res.string.debug_port_input_hint.getSync())
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm.navigator::pop)
                },
                title = { Text(stringResource(Res.string.debug_settings_title)) },
            )
        },
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()),
        ) {
            GkSettingItem(
                title = stringResource(Res.string.debug_port),
                subtitle = store.httpServerPort.toString(),
                imageVector = GkIcons.Edit,
                onClickLabel = stringResource(Res.string.debug_port_edit),
                onClick = {
                    vm.editPort(store.httpServerPort)
                },
            )
            GkTextSwitch(
                title = stringResource(Res.string.debug_clear_subscription),
                subtitle = stringResource(Res.string.debug_clear_subscription_description),
                checked = store.autoClearMemorySubs,
                onCheckedChange = { enabled ->
                    SettingsRepository.updateSettings { it.copy(autoClearMemorySubs = enabled) }
                },
            )
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun EditHttpPortDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    GkAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = stringResource(Res.string.debug_port)) },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(text = stringResource(Res.string.debug_port_input_hint)) },
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().autoFocus(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = stringResource(Res.string.port_input_length, value.length),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = { onConfirm(value) },
            ) {
                Text(text = stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(Res.string.action_cancel))
            }
        },
    )
}
