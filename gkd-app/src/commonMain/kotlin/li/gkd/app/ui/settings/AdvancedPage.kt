package li.gkd.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.settings.GithubCookieStore
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
import li.gkd.app.ui.component.gkSettingsFocus
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.navigation.A11yEventLogRoute
import li.gkd.app.ui.navigation.ActivityLogRoute
import li.gkd.app.ui.navigation.AdvancedSection
import li.gkd.app.ui.navigation.AppNavEntry
import li.gkd.app.ui.navigation.CrashReportRoute
import li.gkd.app.ui.navigation.DebugSettingsRoute
import li.gkd.app.ui.navigation.ExternalCallsRoute
import li.gkd.app.ui.navigation.MatchDebugRoute
import li.gkd.app.ui.navigation.QuickSettingsTilesRoute
import li.gkd.app.ui.navigation.SnapshotPageRoute
import li.gkd.app.ui.navigation.SnapshotSettingsRoute
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.style.TABULAR_NUMBERS_FONT_FEATURE
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.titleItemPadding
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdvancedPage(
    entry: AppNavEntry,
) {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { AdvancedViewModel() }
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val cookie by GithubCookieStore.value.collectAsStateWithLifecycle()
    val debugServiceRunning: Boolean = debugServiceRunning()

    val serverAddresses: List<String> = httpServerAddresses()

    val activityServiceRunning: Boolean = activityMonitorRunning()

    val eventServiceRunning: Boolean = eventMonitorRunning()

    val trackServiceRunning: Boolean = trackServiceRunning()

    val showTrackNotice by vm.showTrackNotice.collectAsStateWithLifecycle()
    if (showTrackNotice)
        GkAlertDialog(
            onDismissRequest = { vm.setShowTrackNotice(false) },
            title = { Text(stringResource(Res.string.usage_notice)) },
            text = { Text(stringResource(Res.string.track_overlay_usage_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setShowTrackNotice(false)
                        host.setTrackServiceEnabled(true)
                    },
                ) {
                    Text(stringResource(Res.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setShowTrackNotice(false) }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val sectionRequest by entry.advancedSectionRequest.collectAsStateWithLifecycle()
    @Composable
    fun sectionFocusModifier(section: AdvancedSection): Modifier =
        Modifier.gkSettingsFocus(sectionRequest == section, entry::consumeAdvancedSection)

    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm.navigator::pop,
                    )
                },
                title = { Text(text = stringResource(Res.string.advanced_settings)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            AdvancedSectionTitle(
                stringResource(Res.string.advanced_snapshot_capture),
                showTop = false,
            )
            GkTextSwitch(
                title = stringResource(Res.string.snapshot_button_label),
                checked = snapshotButtonRunning(),
                onCheckedChange = host::setSnapshotButtonEnabled,
            )
            GkSettingItem(
                title = stringResource(Res.string.snapshot_records),
                onClick = { mainVm.navigator.navigate(SnapshotPageRoute) },
            )
            GkSettingItem(
                title = stringResource(Res.string.snapshot_settings),
                modifier = sectionFocusModifier(AdvancedSection.SnapshotSettings),
                onClick = { mainVm.navigator.navigate(SnapshotSettingsRoute) },
            )
            AdvancedSectionTitle(stringResource(Res.string.advanced_live_debug))
            DebugServiceItem(
                modifier = sectionFocusModifier(AdvancedSection.Http),
                running = debugServiceRunning,
                port = store.httpServerPort,
                serverAddresses = serverAddresses,
                onSettingsClick = { mainVm.navigator.navigate(DebugSettingsRoute) },
                onRunningChange = host::setDebugServiceEnabled,
                onAddressClick = mainVm.textDialog::showUrl,
            )
            AdvancedSectionTitle(stringResource(Res.string.advanced_interface_debug))
            GkTextSwitch(
                modifier = sectionFocusModifier(AdvancedSection.Activity),
                title = stringResource(Res.string.activity_overlay_title),
                checked = activityServiceRunning,
                onCheckedChange = host::setActivityMonitorEnabled,
            )
            GkSettingItem(
                title = stringResource(Res.string.activity_switch_logs),
                onClick = { mainVm.navigator.navigate(ActivityLogRoute) },
            )
            GkTextSwitch(
                modifier = sectionFocusModifier(AdvancedSection.Event),
                title = stringResource(Res.string.event_overlay_title),
                checked = eventServiceRunning,
                onCheckedChange = host::setEventMonitorEnabled,
            )
            GkSettingItem(
                title = stringResource(Res.string.a11y_event_logs),
                onClick = { mainVm.navigator.navigate(A11yEventLogRoute) },
            )
            GkTextSwitch(
                modifier = sectionFocusModifier(AdvancedSection.Track),
                title = stringResource(Res.string.track_overlay),
                checked = trackServiceRunning,
                onCheckedChange = {
                    if (it) vm.setShowTrackNotice(true) else host.setTrackServiceEnabled(false)
                },
            )
            AdvancedSectionTitle(stringResource(Res.string.advanced_logs_diagnostics))
            DiagnosticCapture(vm, sectionFocusModifier(AdvancedSection.Diagnostics))
            GkSettingItem(
                title = stringResource(Res.string.match_debug_records),
                onClick = { mainVm.navigator.navigate(MatchDebugRoute) },
            )
            GkSettingItem(
                title = stringResource(Res.string.crash_reports),
                onClick = { mainVm.navigator.navigate(CrashReportRoute) },
            )
            AdvancedSectionTitle(stringResource(Res.string.integration_tools_title))
            GkSettingItem(
                title = stringResource(Res.string.quick_settings_tiles_title),
                onClick = { mainVm.navigator.navigate(QuickSettingsTilesRoute) },
            )
            GkSettingItem(
                title = stringResource(Res.string.external_calls_title),
                onClick = { mainVm.navigator.navigate(ExternalCallsRoute) },
            )
            AdvancedSectionTitle(stringResource(Res.string.advanced_upload_share))
            GkSettingItem(
                title = stringResource(Res.string.github_cookie_label),
                subtitle =
                    stringResource(
                        if (cookie.isNotEmpty()) Res.string.upload_configured
                        else Res.string.upload_not_configured,
                    ),
                imageVector = GkIcons.Edit,
                onClick = mainVm.githubUpload::editCookie,
            )
            GkSettingItem(
                title = stringResource(Res.string.tutorial_view),
                onClick = { mainVm.navigator.openWebPage(AppLinks.CookieHelp) },
            )
            GkPageBottomSpace()
        }
    }
}

expect fun UiHost.setSnapshotButtonEnabled(enabled: Boolean)

expect fun UiHost.setDebugServiceEnabled(enabled: Boolean)

expect fun httpServerPortChanged()

expect fun UiHost.setActivityMonitorEnabled(enabled: Boolean)

expect fun UiHost.setEventMonitorEnabled(enabled: Boolean)

expect fun UiHost.setTrackServiceEnabled(enabled: Boolean)

@Composable expect fun debugServiceRunning(): Boolean

@Composable expect fun httpServerAddresses(): List<String>

@Composable expect fun snapshotButtonRunning(): Boolean

@Composable expect fun activityMonitorRunning(): Boolean

@Composable expect fun eventMonitorRunning(): Boolean

@Composable expect fun trackServiceRunning(): Boolean

@Composable
private fun DebugServiceItem(
    modifier: Modifier,
    running: Boolean,
    port: Int,
    serverAddresses: List<String>,
    onSettingsClick: () -> Unit,
    onRunningChange: (Boolean) -> Unit,
    onAddressClick: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        GkTextSwitch(
            title = stringResource(Res.string.debug_service_compact_label),
            checked = running,
            onCheckedChange = onRunningChange,
        )
        GkSettingItem(
            title = stringResource(Res.string.debug_settings_title),
            onClick = onSettingsClick,
        )
        AnimatedVisibility(visible = running) {
            DebugServerAddresses(
                hosts = serverAddresses,
                port = port,
                onAddressClick = onAddressClick,
                modifier =
                    Modifier.padding(
                        start = itemHorizontalPadding,
                        top = 0.dp,
                        end = itemHorizontalPadding,
                        bottom = itemVerticalPadding,
                    ),
            )
        }
    }
}

@Composable
private fun DebugServerAddresses(
    hosts: List<String>,
    port: Int,
    onAddressClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addresses = hosts.map {
        stringResource(Res.string.debug_address_description, it, port.toString())
    }
    val style =
        MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMBERS_FONT_FEATURE)
    val textMeasurer = rememberTextMeasurer()
    val widestText = addresses.maxOf {
        textMeasurer.measure(text = it, style = style, softWrap = false).size.width
    }
    val horizontalPadding = 4.dp
    val spacing = 12.dp
    val minItemWidth = with(LocalDensity.current) { widestText.toDp() } + horizontalPadding * 2
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minItemWidth + spacing)).toInt().coerceAtLeast(1)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            maxItemsInEachRow = columns,
        ) {
            addresses.forEachIndexed { index, address ->
                Text(
                    text = address,
                    style = style,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.weight(1f)
                            .clickable(
                                onClickLabel =
                                    stringResource(Res.string.debug_view_addresses, address),
                                onClick = { onAddressClick("http://${hosts[index]}:$port") },
                            )
                            .padding(horizontal = horizontalPadding, vertical = 6.dp),
                )
            }
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
private fun DiagnosticCapture(vm: AdvancedViewModel, modifier: Modifier) {
    val diagnostics = MatchingRuntime.coordinator.diagnostics
    val control =
        remember(diagnostics) {
            diagnostics.state.map { it?.id to (it?.active == true) }.distinctUntilChanged()
        }
    val current by
        control.collectAsStateWithLifecycle(
            initialValue = diagnostics.state.value.let { it?.id to (it?.active == true) },
        )
    GkTextSwitch(
        modifier = modifier,
        title = stringResource(Res.string.match_debug_collect),
        checked = current.second,
        onCheckedChange = { enabled ->
            if (enabled) vm.requestDiagnosticCapture()
            else current.first?.let { MatchingRuntime.coordinator.stopDiagnostics(it) }
        },
    )
}
