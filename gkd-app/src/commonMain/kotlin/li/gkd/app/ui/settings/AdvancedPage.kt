package li.gkd.app.ui.settings

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_event_logs
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_confirm
import li.gkd.app.resources.action_continue
import li.gkd.app.resources.activity_log_title
import li.gkd.app.resources.activity_service_description
import li.gkd.app.resources.activity_service_label
import li.gkd.app.resources.activity_switch_logs
import li.gkd.app.resources.advanced_live_debug
import li.gkd.app.resources.advanced_logs_diagnostics
import li.gkd.app.resources.advanced_settings
import li.gkd.app.resources.advanced_snapshot_capture
import li.gkd.app.resources.advanced_upload_share
import li.gkd.app.resources.crash_reports
import li.gkd.app.resources.crash_reports_description
import li.gkd.app.resources.event_log_title
import li.gkd.app.resources.event_service_description
import li.gkd.app.resources.event_service_label
import li.gkd.app.resources.github_cookie_label
import li.gkd.app.resources.http_address_description
import li.gkd.app.resources.http_clear_subscription
import li.gkd.app.resources.http_clear_subscription_description
import li.gkd.app.resources.http_port
import li.gkd.app.resources.http_port_edit
import li.gkd.app.resources.http_port_input_hint
import li.gkd.app.resources.http_service_description
import li.gkd.app.resources.http_service_label
import li.gkd.app.resources.http_settings_button
import li.gkd.app.resources.http_settings_open
import li.gkd.app.resources.http_settings_title
import li.gkd.app.resources.http_toggle_service
import li.gkd.app.resources.http_view_addresses
import li.gkd.app.resources.network_lan
import li.gkd.app.resources.network_local_device
import li.gkd.app.resources.port_input_length
import li.gkd.app.resources.snapshot_button_description
import li.gkd.app.resources.snapshot_button_label
import li.gkd.app.resources.snapshot_records
import li.gkd.app.resources.snapshot_records_description
import li.gkd.app.resources.snapshot_settings
import li.gkd.app.resources.snapshot_settings_description
import li.gkd.app.resources.track_overlay
import li.gkd.app.resources.track_overlay_description
import li.gkd.app.resources.track_overlay_usage_description
import li.gkd.app.resources.tutorial_view
import li.gkd.app.resources.update_success
import li.gkd.app.resources.upload_links_description
import li.gkd.app.resources.usage_notice
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkSettingsDialog
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.navigation.A11yEventLogRoute
import li.gkd.app.ui.navigation.ActivityLogRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.CrashReportRoute
import li.gkd.app.ui.navigation.SnapshotPageRoute
import li.gkd.app.ui.navigation.SnapshotSettingsRoute
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.openExternalUrl
import li.gkd.app.ui.style.TABULAR_NUMBERS_FONT_FEATURE
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.Constants
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdvancedPage(
    window: AppWindow,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit,
    onCookieEdit: () -> Unit,
) {
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val httpRunning: Boolean = window.httpRunning()

    val localNetworkIps: List<String> = window.localNetworkIps()

    val buttonServiceRunning: Boolean = window.snapshotButtonRunning()

    val activityServiceRunning: Boolean = window.activityMonitorRunning()

    val eventServiceRunning: Boolean = window.eventMonitorRunning()

    val trackServiceRunning: Boolean = window.trackServiceRunning()

    var showTrackNotice by rememberSaveable { mutableStateOf(false) }
    if (showTrackNotice)
        GkAlertDialog(
            onDismissRequest = { showTrackNotice = false },
            title = { Text(stringResource(Res.string.usage_notice)) },
            text = { Text(stringResource(Res.string.track_overlay_usage_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrackNotice = false
                        window.setTrackServiceEnabled(true)
                    }
                ) {
                    Text(stringResource(Res.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrackNotice = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    var showEditPortDialog by rememberSaveable { mutableStateOf(false) }
    var showHttpSettingsDialog by rememberSaveable { mutableStateOf(false) }

    if (showHttpSettingsDialog) {
        GkSettingsDialog(
            title = stringResource(Res.string.http_settings_title),
            onDismissRequest = { showHttpSettingsDialog = false },
        ) {
            GkSettingItem(
                title = stringResource(Res.string.http_port),
                subtitle = store.httpServerPort.toString(),
                imageVector = GkIcons.Edit,
                onClickLabel = stringResource(Res.string.http_port_edit),
                onClick = {
                    showEditPortDialog = true
                },
            )
            GkTextSwitch(
                title = stringResource(Res.string.http_clear_subscription),
                subtitle = stringResource(Res.string.http_clear_subscription_description),
                checked = store.autoClearMemorySubs,
                onCheckedChange = { enabled ->
                    SettingsRepository.updateSettings { it.copy(autoClearMemorySubs = enabled) }
                },
            )
        }
    }

    if (showEditPortDialog) {
        EditHttpPortDialog(
            currentPort = store.httpServerPort,
            onDismissRequest = { showEditPortDialog = false },
            onConfirm = { text ->
                val oldPort = SettingsRepository.settings.value.httpServerPort
                if (SettingsRepository.saveHttpServerPort(text)) {
                    if (oldPort != text.toInt()) toast(Res.string.update_success.getSync())
                    showEditPortDialog = false
                } else {
                    toast(Res.string.http_port_input_hint.getSync())
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = onBack,
                    )
                },
                title = { Text(text = stringResource(Res.string.advanced_settings)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
        ) {
            AdvancedSectionTitle(
                stringResource(Res.string.advanced_snapshot_capture),
                showTop = false,
            )
            GkSettingItem(
                title = stringResource(Res.string.snapshot_records),
                subtitle = stringResource(Res.string.snapshot_records_description),
                onClick = { onNavigate(SnapshotPageRoute) },
            )
            GkTextSwitch(
                title = stringResource(Res.string.snapshot_button_label),
                subtitle = stringResource(Res.string.snapshot_button_description),
                checked = buttonServiceRunning,
                onCheckedChange = window::setSnapshotButtonEnabled,
            )
            GkSettingItem(
                title = stringResource(Res.string.snapshot_settings),
                subtitle = stringResource(Res.string.snapshot_settings_description),
                onClick = { onNavigate(SnapshotSettingsRoute) },
            )

            AdvancedSectionTitle(stringResource(Res.string.advanced_live_debug))
            HttpServiceItem(
                running = httpRunning,
                settingsSelected = showHttpSettingsDialog,
                port = store.httpServerPort,
                localNetworkIps = localNetworkIps,
                onSettingsClick = { showHttpSettingsDialog = true },
                onRunningChange = window::setHttpServiceEnabled,
                onAddressClick = window::openExternalUrl,
            )
            GkTextSwitch(
                title = stringResource(Res.string.activity_service_label),
                subtitle = stringResource(Res.string.activity_service_description),
                checked = activityServiceRunning,
                onCheckedChange = window::setActivityMonitorEnabled,
            )
            GkTextSwitch(
                title = stringResource(Res.string.event_service_label),
                subtitle = stringResource(Res.string.event_service_description),
                checked = eventServiceRunning,
                onCheckedChange = window::setEventMonitorEnabled,
            )
            GkTextSwitch(
                title = stringResource(Res.string.track_overlay),
                subtitle = stringResource(Res.string.track_overlay_description),
                checked = trackServiceRunning,
                onCheckedChange = { if (it) showTrackNotice = true else window.setTrackServiceEnabled(false) },
            )

            AdvancedSectionTitle(stringResource(Res.string.advanced_logs_diagnostics))
            GkSettingItem(
                title = stringResource(Res.string.activity_log_title),
                subtitle = stringResource(Res.string.activity_switch_logs),
                onClick = { onNavigate(ActivityLogRoute) },
            )
            GkSettingItem(
                title = stringResource(Res.string.event_log_title),
                subtitle = stringResource(Res.string.a11y_event_logs),
                onClick = { onNavigate(A11yEventLogRoute) },
            )
            GkSettingItem(
                title = stringResource(Res.string.crash_reports),
                subtitle = stringResource(Res.string.crash_reports_description),
                onClick = { onNavigate(CrashReportRoute) },
            )

            AdvancedSectionTitle(stringResource(Res.string.advanced_upload_share))
            GkSettingItem(
                title = stringResource(Res.string.github_cookie_label),
                subtitle = stringResource(Res.string.upload_links_description),
                suffix = stringResource(Res.string.tutorial_view),
                suffixUnderline = true,
                onSuffixClick = { onNavigate(WebViewRoute(AppLinks.CookieHelp)) },
                imageVector = GkIcons.Edit,
                onClick = onCookieEdit,
            )
            GkPageBottomSpace()
        }
    }
}

expect fun AppWindow.setSnapshotButtonEnabled(enabled: Boolean)

expect fun AppWindow.setHttpServiceEnabled(enabled: Boolean)

expect fun AppWindow.setActivityMonitorEnabled(enabled: Boolean)

expect fun AppWindow.setEventMonitorEnabled(enabled: Boolean)

expect fun AppWindow.setTrackServiceEnabled(enabled: Boolean)

@Composable expect fun AppWindow.httpRunning(): Boolean

@Composable expect fun AppWindow.localNetworkIps(): List<String>

@Composable expect fun AppWindow.snapshotButtonRunning(): Boolean

@Composable expect fun AppWindow.activityMonitorRunning(): Boolean

@Composable expect fun AppWindow.eventMonitorRunning(): Boolean

@Composable expect fun AppWindow.trackServiceRunning(): Boolean

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
    val addressStyle =
        MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMBERS_FONT_FEATURE)
    val addressItem: @Composable (String, String) -> Unit = { host, type ->
        Text(
            text = stringResource(Res.string.http_address_description, host, port, type),
            style = addressStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(Res.string.http_view_addresses, type),
                        onClick = { onAddressClick("http://${host}:${port}") },
                    )
                    .padding(vertical = 2.dp),
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(Res.string.http_toggle_service),
                        onClick = { onRunningChange(!running) },
                    )
                    .padding(
                        start = itemHorizontalPadding,
                        top = itemVerticalPadding,
                        end = itemHorizontalPadding,
                        bottom = 4.dp,
                    )
        ) {
            GkTextSwitch(
                modifier = Modifier.fillMaxWidth(),
                paddingDisabled = true,
                title = stringResource(Res.string.http_service_label),
                subtitle = stringResource(Res.string.http_service_description),
                suffixIcon = {
                    GkSizedIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = stringResource(Res.string.http_settings_open),
                        onClick = onSettingsClick,
                        imageVector = GkIcons.PageInfo,
                        contentDescription = stringResource(Res.string.http_settings_button),
                        tint =
                            if (settingsSelected) {
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
                modifier =
                    Modifier.padding(
                        start = itemHorizontalPadding,
                        top = 0.dp,
                        end = itemHorizontalPadding,
                        bottom = 4.dp,
                    )
            ) {
                addressItem(Constants.loopbackHost, stringResource(Res.string.network_local_device))
                localNetworkIps.forEach { host ->
                    addressItem(host, stringResource(Res.string.network_lan))
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
        title = { Text(text = stringResource(Res.string.http_port)) },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(text = stringResource(Res.string.http_port_input_hint)) },
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
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
