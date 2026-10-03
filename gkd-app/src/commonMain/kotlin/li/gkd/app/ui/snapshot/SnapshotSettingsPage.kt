package li.gkd.app.ui.snapshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_confirm
import li.gkd.app.resources.action_export
import li.gkd.app.resources.app_id
import li.gkd.app.resources.app_id_invalid
import li.gkd.app.resources.event_selector_invalid
import li.gkd.app.resources.screenshot_processing
import li.gkd.app.resources.screenshot_service
import li.gkd.app.resources.screenshot_service_description
import li.gkd.app.resources.snapshot_auto_export
import li.gkd.app.resources.snapshot_auto_export_description
import li.gkd.app.resources.snapshot_capture_methods
import li.gkd.app.resources.snapshot_event_selector
import li.gkd.app.resources.snapshot_event_selector_hint
import li.gkd.app.resources.snapshot_hide_status_bar
import li.gkd.app.resources.snapshot_hide_status_bar_description
import li.gkd.app.resources.snapshot_screenshot_settings
import li.gkd.app.resources.snapshot_screenshot_settings_open
import li.gkd.app.resources.snapshot_screenshot_trigger
import li.gkd.app.resources.snapshot_screenshot_trigger_description
import li.gkd.app.resources.snapshot_settings
import li.gkd.app.resources.snapshot_trigger_config_required
import li.gkd.app.resources.snapshot_volume_trigger
import li.gkd.app.resources.snapshot_volume_trigger_description
import li.gkd.app.resources.target_app_id_hint
import li.gkd.app.resources.update_success
import li.gkd.app.settings.ScreenshotConfigResult
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkOutlinedTextField
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.ui.text.getSync
import org.jetbrains.compose.resources.stringResource

@Composable
fun SnapshotSettingsPage(
    window: AppWindow,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val screenshotServiceRunning: Boolean = window.screenshotServiceRunning()

    val nativeScreenshotAvailable: Boolean = window.nativeScreenshotAvailable()

    var showCaptureScreenshotDialog by rememberSaveable { mutableStateOf(false) }

    if (showCaptureScreenshotDialog) {
        CaptureScreenshotConfigDialog(
            appId = store.screenshotTargetAppId,
            eventSelector = store.screenshotEventSelector,
            onOpenHelp = {
                showCaptureScreenshotDialog = false
                onNavigate(WebViewRoute(AppLinks.SnapshotHelp))
            },
            onDismissRequest = { showCaptureScreenshotDialog = false },
            onConfirm = { appId, selector ->
                when (SettingsRepository.saveCaptureScreenshotConfig(appId, selector)) {
                    ScreenshotConfigResult.Unchanged -> showCaptureScreenshotDialog = false
                    ScreenshotConfigResult.Saved -> {
                        toast(Res.string.update_success.getSync())
                        showCaptureScreenshotDialog = false
                    }
                    ScreenshotConfigResult.InvalidApp -> toast(Res.string.app_id_invalid.getSync())
                    ScreenshotConfigResult.InvalidSelector ->
                        toast(Res.string.event_selector_invalid.getSync())
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
                title = { Text(text = stringResource(Res.string.snapshot_settings)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)
        ) {
            Text(
                text = stringResource(Res.string.snapshot_capture_methods),
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!nativeScreenshotAvailable) {
                GkTextSwitch(
                    title = stringResource(Res.string.screenshot_service),
                    subtitle = stringResource(Res.string.screenshot_service_description),
                    checked = screenshotServiceRunning,
                    onCheckedChange = window::setScreenshotServiceEnabled,
                )
            }
            GkTextSwitch(
                title = stringResource(Res.string.snapshot_volume_trigger),
                subtitle = stringResource(Res.string.snapshot_volume_trigger_description),
                checked = store.captureVolumeChange,
                onCheckedChange = { enabled ->
                    SettingsRepository.updateSettings {
                        it.copy(captureVolumeChange = enabled)
                    }
                },
            )
            GkTextSwitch(
                title = stringResource(Res.string.snapshot_screenshot_trigger),
                subtitle = stringResource(Res.string.snapshot_screenshot_trigger_description),
                checked = store.captureScreenshot,
                suffixIcon = {
                    GkSizedIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = stringResource(Res.string.snapshot_screenshot_settings_open),
                        onClick = { showCaptureScreenshotDialog = true },
                        imageVector = GkIcons.PageInfo,
                        contentDescription =
                            stringResource(Res.string.snapshot_screenshot_settings),
                    )
                },
                onCheckedChange = { enabled ->
                    SettingsRepository.updateSettings { it.copy(captureScreenshot = enabled) }
                    val current = SettingsRepository.settings.value
                    if (
                        enabled &&
                            (current.screenshotTargetAppId.isEmpty() ||
                                current.screenshotEventSelector.isEmpty())
                    ) {
                        toast(Res.string.snapshot_trigger_config_required.getSync())
                    }
                },
            )

            Text(
                text = stringResource(Res.string.screenshot_processing),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextSwitch(
                title = stringResource(Res.string.snapshot_hide_status_bar),
                subtitle = stringResource(Res.string.snapshot_hide_status_bar_description),
                checked = store.hideSnapshotStatusBar,
                onCheckedChange = { enabled ->
                    SettingsRepository.updateSettings {
                        it.copy(hideSnapshotStatusBar = enabled)
                    }
                },
            )

            Text(
                text = stringResource(Res.string.action_export),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextSwitch(
                title = stringResource(Res.string.snapshot_auto_export),
                subtitle = stringResource(Res.string.snapshot_auto_export_description),
                checked = store.autoSaveSnapshotToDownloads,
                onCheckedChange = { enabled ->
                    launchUi(scope, toast) {
                        if (!enabled || window.ensureSnapshotSavePermission())
                            SettingsRepository.updateSettings {
                                it.copy(autoSaveSnapshotToDownloads = enabled)
                            }
                    }
                },
            )
            GkPageBottomSpace()
        }
    }
}

@Composable expect fun AppWindow.screenshotServiceRunning(): Boolean

expect fun AppWindow.nativeScreenshotAvailable(): Boolean

expect fun AppWindow.setScreenshotServiceEnabled(enabled: Boolean)

expect suspend fun AppWindow.ensureSnapshotSavePermission(): Boolean

@Composable
private fun CaptureScreenshotConfigDialog(
    appId: String,
    eventSelector: String,
    onOpenHelp: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var appIdValue by remember { mutableStateOf(appId) }
    var eventSelectorValue by remember { mutableStateOf(eventSelector) }
    GkAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.snapshot_screenshot_trigger))
                GkIconButton(
                    imageVector = GkIcons.HelpOutline,
                    onClick = onOpenHelp,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GkOutlinedTextField(
                    label = { Text(stringResource(Res.string.app_id)) },
                    value = appIdValue,
                    placeholder = { Text(text = stringResource(Res.string.target_app_id_hint)) },
                    onValueChange = { appIdValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                GkOutlinedTextField(
                    label = { Text(stringResource(Res.string.snapshot_event_selector)) },
                    value = eventSelectorValue,
                    placeholder = {
                        Text(text = stringResource(Res.string.snapshot_event_selector_hint))
                    },
                    onValueChange = { eventSelectorValue = it },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onConfirm(appIdValue, eventSelectorValue) }) {
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
