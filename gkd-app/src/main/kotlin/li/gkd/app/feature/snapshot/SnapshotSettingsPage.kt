package li.gkd.app.feature.snapshot

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import android.app.Activity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.ScreenshotService
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkOutlinedTextField
import li.gkd.app.ui.component.GkSizedIconButton
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.autoFocus

@Serializable
data object SnapshotSettingsRoute : NavKey

@Composable
fun SnapshotSettingsPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<SnapshotSettingsVm>()
    val scope = vm.scope
    val store by storeFlow.collectAsStateWithLifecycle()
    val screenshotServiceRunning by ScreenshotService.isRunning.collectAsStateWithLifecycle()
    var showCaptureScreenshotDialog by rememberSaveable { mutableStateOf(false) }

    fun setScreenshotServiceEnabled(enabled: Boolean) {
        scope.launchUi {
            if (!enabled) {
                ScreenshotService.stop()
                return@launchUi
            }
            if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.notification)) {
                return@launchUi
            }
            val activityResult = mainVm.activityResults.startActivity(
                app.mediaProjectionManager.createScreenCaptureIntent(),
            )
            val intent = activityResult.data
            if (activityResult.resultCode == Activity.RESULT_OK && intent != null) {
                ScreenshotService.start(intent)
            }
        }
    }

    if (showCaptureScreenshotDialog) {
        CaptureScreenshotConfigDialog(
            appId = store.screenshotTargetAppId,
            eventSelector = store.screenshotEventSelector,
            onOpenHelp = {
                showCaptureScreenshotDialog = false
                mainVm.navigateWebPage(ShortUrlSet.URL15)
            },
            onDismissRequest = { showCaptureScreenshotDialog = false },
            onConfirm = { appId, selector ->
                if (vm.saveCaptureScreenshotConfig(appId, selector)) {
                    showCaptureScreenshotDialog = false
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
                title = { Text(text = UiStrings.snapshot_settings) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Text(
                text = UiStrings.snapshot_capture_methods,
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!AndroidTarget.R) {
                GkTextSwitch(
                    title = UiStrings.screenshot_service,
                    subtitle = UiStrings.screenshot_service_description,
                    checked = screenshotServiceRunning,
                    onCheckedChange = ::setScreenshotServiceEnabled,
                )
            }
            GkTextSwitch(
                title = UiStrings.snapshot_volume_trigger,
                subtitle = UiStrings.snapshot_volume_trigger_description,
                checked = store.captureVolumeChange,
                onCheckedChange = vm::setCaptureVolumeChange,
            )
            GkTextSwitch(
                title = UiStrings.snapshot_screenshot_trigger,
                subtitle = UiStrings.snapshot_screenshot_trigger_description,
                checked = store.captureScreenshot,
                suffixIcon = {
                    GkSizedIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = UiStrings.snapshot_screenshot_settings_open,
                        onClick = throttle { showCaptureScreenshotDialog = true },
                        imageVector = GkIcons.PageInfo,
                        contentDescription = UiStrings.snapshot_screenshot_settings,
                    )
                },
                onCheckedChange = vm::setCaptureScreenshot,
            )

            Text(
                text = UiStrings.screenshot_processing,
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextSwitch(
                title = UiStrings.snapshot_hide_status_bar,
                subtitle = UiStrings.snapshot_hide_status_bar_description,
                checked = store.hideSnapshotStatusBar,
                onCheckedChange = vm::setHideSnapshotStatusBar,
            )

            Text(
                text = UiStrings.action_export,
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextSwitch(
                title = UiStrings.snapshot_auto_export,
                subtitle = UiStrings.snapshot_auto_export_description,
                checked = store.autoSaveSnapshotToDownloads,
                onCheckedChange = scope.launchUiAction { enabled ->
                    if (
                        !enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.writeExternalStorage,
                        )
                    ) {
                        vm.setAutoSaveSnapshotToDownloads(enabled)
                    }
                },
            )
            GkPageBottomSpace()
        }
    }
}

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
                Text(text = UiStrings.snapshot_screenshot_trigger)
                GkIconButton(
                    imageVector = GkIcons.HelpOutline,
                    onClick = throttle(onOpenHelp),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                GkOutlinedTextField(
                    label = { Text(UiStrings.app_id) },
                    value = appIdValue,
                    placeholder = { Text(text = UiStrings.target_app_id_hint) },
                    onValueChange = { appIdValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                GkOutlinedTextField(
                    label = { Text(UiStrings.snapshot_event_selector) },
                    value = eventSelectorValue,
                    placeholder = { Text(text = UiStrings.snapshot_event_selector_hint) },
                    onValueChange = { eventSelectorValue = it },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = throttle { onConfirm(appIdValue, eventSelectorValue) },
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
