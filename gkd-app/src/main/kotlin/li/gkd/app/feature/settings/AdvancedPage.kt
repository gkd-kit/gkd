package li.gkd.app.feature.settings

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.feature.log.A11yEventLogRoute
import li.gkd.app.feature.log.ActivityLogRoute
import li.gkd.app.feature.snapshot.SnapshotPageRoute
import li.gkd.app.feature.snapshot.SnapshotSettingsRoute
import li.gkd.app.ui.CrashReportRoute
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.service.ServiceController
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.EventService
import li.gkd.app.service.TrackService
import li.gkd.app.ui.share.launchUi
import li.gkd.app.service.HttpService
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.style.TABULAR_NUMBERS_FONT_FEATURE
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkSettingsDialog
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus

@Serializable
data object AdvancedPageRoute : NavKey

@Composable
fun AdvancedPage() {
    AdvancedContent()
}

@Composable
private fun AdvancedContent() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<AdvancedVm>()
    val scope = vm.scope
    var showEditPortDialog by rememberSaveable { mutableStateOf(false) }
    var showHttpSettingsDialog by rememberSaveable { mutableStateOf(false) }
    val store by storeFlow.collectAsStateWithLifecycle()
    val httpServer by HttpService.httpServerFlow.collectAsStateWithLifecycle()
    val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsStateWithLifecycle()
    val buttonServiceRunning by ButtonService.isRunning.collectAsStateWithLifecycle()
    val activityServiceRunning by ActivityService.isRunning.collectAsStateWithLifecycle()
    val eventServiceRunning by EventService.isRunning.collectAsStateWithLifecycle()
    val trackServiceRunning by TrackService.isRunning.collectAsStateWithLifecycle()

    if (showHttpSettingsDialog) {
        GkSettingsDialog(
            title = UiStrings.http_settings_title,
            onDismissRequest = { showHttpSettingsDialog = false },
        ) {
            GkSettingItem(
                title = UiStrings.http_port,
                subtitle = store.httpServerPort.toString(),
                imageVector = GkIcons.Edit,
                onClickLabel = UiStrings.http_port_edit,
                onClick = {
                    showEditPortDialog = true
                },
            )
            GkTextSwitch(
                title = UiStrings.http_clear_subscription,
                subtitle = UiStrings.http_clear_subscription_description,
                checked = store.autoClearMemorySubs,
                onCheckedChange = vm::setAutoClearMemorySubs,
            )
        }
    }

    if (showEditPortDialog) {
        EditHttpPortDialog(
            currentPort = store.httpServerPort,
            onDismissRequest = { showEditPortDialog = false },
            onConfirm = {
                if (vm.saveHttpServerPort(it)) {
                    showEditPortDialog = false
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = { Text(text = UiStrings.advanced_settings) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            AdvancedSectionTitle(UiStrings.advanced_snapshot_capture, showTop = false)
            GkSettingItem(
                title = UiStrings.snapshot_records,
                subtitle = UiStrings.snapshot_records_description,
                onClick = { mainVm.navigatePage(SnapshotPageRoute) },
            )
            GkTextSwitch(
                title = UiStrings.snapshot_button_label,
                subtitle = UiStrings.snapshot_button_description,
                checked = buttonServiceRunning,
                onCheckedChange = scope.launchUiAction { enabled ->
                    if (!enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.foregroundServiceSpecialUse,
                            PermissionStates.notification,
                            PermissionStates.drawOverlays,
                        )
                    ) {
                        ServiceController.setSnapshotButtonEnabled(enabled)
                    }
                },
            )
            GkSettingItem(
                title = UiStrings.snapshot_settings,
                subtitle = UiStrings.snapshot_settings_description,
                onClick = { mainVm.navigatePage(SnapshotSettingsRoute) },
            )

            AdvancedSectionTitle(UiStrings.advanced_live_debug)
            HttpServiceItem(
                running = httpServer != null,
                settingsSelected = showHttpSettingsDialog,
                port = store.httpServerPort,
                localNetworkIps = localNetworkIps,
                onSettingsClick = { showHttpSettingsDialog = true },
                onRunningChange = throttle(fn = scope.launchUiAction { enabled ->
                    if (!enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.foregroundServiceSpecialUse,
                            PermissionStates.notification,
                            PermissionStates.localNetwork,
                        )
                    ) {
                        ServiceController.setHttpEnabled(enabled)
                    }
                }),
                onAddressClick = mainVm::openUrl,
            )
            GkTextSwitch(
                title = UiStrings.activity_service_label,
                subtitle = UiStrings.activity_service_description,
                checked = activityServiceRunning,
                onCheckedChange = scope.launchUiAction { enabled ->
                    if (!enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.foregroundServiceSpecialUse,
                            PermissionStates.notification,
                            PermissionStates.drawOverlays,
                        )
                    ) {
                        ServiceController.setActivityMonitorEnabled(enabled)
                    }
                },
            )
            GkTextSwitch(
                title = UiStrings.event_service_label,
                subtitle = UiStrings.event_service_description,
                checked = eventServiceRunning,
                onCheckedChange = scope.launchUiAction { enabled ->
                    if (!enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.foregroundServiceSpecialUse,
                            PermissionStates.notification,
                            PermissionStates.drawOverlays,
                        )
                    ) {
                        ServiceController.setEventMonitorEnabled(enabled)
                    }
                },
            )
            GkTextSwitch(
                title = UiStrings.track_overlay,
                subtitle = UiStrings.track_overlay_description,
                checked = trackServiceRunning,
                onCheckedChange = { enabled ->
                    scope.launchUi {
                        if (enabled) {
                            if (!mainVm.dialogRequests.confirm(
                                title = UiStrings.usage_notice,
                                text = UiStrings.track_overlay_usage_description,
                                confirmText = UiStrings.action_continue,
                            )) return@launchUi
                            if (
                                !mainVm.permissionRequests.ensurePermissions(
                                    PermissionStates.foregroundServiceSpecialUse,
                                    PermissionStates.notification,
                                    PermissionStates.drawOverlays,
                                )
                            ) {
                                return@launchUi
                            }
                        }
                        vm.setTrackServiceEnabled(enabled)
                    }
                },
            )

            AdvancedSectionTitle(UiStrings.advanced_logs_diagnostics)
            GkSettingItem(
                title = UiStrings.activity_log_title,
                subtitle = UiStrings.activity_switch_logs,
                onClick = { mainVm.navigatePage(ActivityLogRoute) },
            )
            GkSettingItem(
                title = UiStrings.event_log_title,
                subtitle = UiStrings.a11y_event_logs,
                onClick = { mainVm.navigatePage(A11yEventLogRoute) },
            )
            GkSettingItem(
                title = UiStrings.crash_reports,
                subtitle = UiStrings.crash_reports_description,
                onClick = { mainVm.navigatePage(CrashReportRoute) },
            )

            AdvancedSectionTitle(UiStrings.advanced_upload_share)
            GkSettingItem(
                title = UiStrings.github_cookie_label,
                subtitle = UiStrings.upload_links_description,
                suffix = UiStrings.tutorial_view,
                suffixUnderline = true,
                onSuffixClick = mainVm.githubUpload::openCookieHelp,
                imageVector = GkIcons.Edit,
                onClick = mainVm.githubUpload::editCookie,
            )
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun AdvancedSectionTitle(title: String, showTop: Boolean = true) {
    Text(
        text = title,
        modifier = Modifier.titleItemPadding(showTop = showTop),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun HttpServiceItem(
    running: Boolean,
    settingsSelected: Boolean,
    port: Int,
    localNetworkIps: List<String>,
    onSettingsClick: () -> Unit,
    onRunningChange: (Boolean) -> Unit,
    onAddressClick: (String) -> Unit,
) {
    val addressStyle = MaterialTheme.typography.bodySmall.copy(
        fontFeatureSettings = TABULAR_NUMBERS_FONT_FEATURE,
    )
    val addressItem: @Composable (String, String) -> Unit = { host, type ->
        Text(
            text = UiStrings.http_address_description(host, port, type),
            style = addressStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = UiStrings.http_view_addresses(type),
                    onClick = throttle { onAddressClick("http://${host}:${port}") },
                )
                .padding(vertical = 2.dp),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = UiStrings.http_toggle_service,
                    onClick = { onRunningChange(!running) },
                )
                .padding(
                    start = itemHorizontalPadding,
                    top = itemVerticalPadding,
                    end = itemHorizontalPadding,
                    bottom = 4.dp,
                ),
        ) {
            GkTextSwitch(
                modifier = Modifier.fillMaxWidth(),
                paddingDisabled = true,
                title = UiStrings.http_service_label,
                subtitle = UiStrings.http_service_description,
                suffixIcon = {
                    GkSizedIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = UiStrings.http_settings_open,
                        onClick = onSettingsClick,
                        imageVector = GkIcons.PageInfo,
                        contentDescription = UiStrings.http_settings_button,
                        tint = if (settingsSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                },
                checked = running,
                onCheckedChange = onRunningChange,
                onClick = null,
            )
        }
        AnimatedVisibility(visible = running) {
            Column(
                modifier = Modifier.padding(
                    start = itemHorizontalPadding,
                    top = 0.dp,
                    end = itemHorizontalPadding,
                    bottom = 4.dp,
                ),
            ) {
                addressItem("127.0.0.1", UiStrings.network_local_device)
                localNetworkIps.forEach { host ->
                    addressItem(host, UiStrings.network_lan)
                }
            }
        }
    }
}

@Composable
private fun EditHttpPortDialog(
    currentPort: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentPort.toString()) }
    GkAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = UiStrings.http_port) },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(text = UiStrings.http_port_input_hint) },
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = UiStrings.port_input_length(value.length),
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
                Text(text = UiStrings.action_confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = UiStrings.action_cancel)
            }
        },
    )
}
