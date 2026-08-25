package li.songe.gkd.ui

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
import li.songe.gkd.R
import li.songe.gkd.app
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.ui.component.CustomOutlinedTextField
import li.songe.gkd.ui.component.PerfCustomIconButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle

@Serializable
data object SnapshotSettingsRoute : NavKey

@Composable
fun SnapshotSettingsPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SnapshotSettingsVm>()
    val scope = vm.scope
    val store by storeFlow.collectAsStateWithLifecycle()
    val screenshotServiceRunning by ScreenshotService.isRunning.collectAsStateWithLifecycle()
    var showCaptureScreenshotDialog by rememberSaveable { mutableStateOf(false) }

    fun setScreenshotServiceEnabled(enabled: Boolean) {
        scope.launchTry {
            if (!enabled) {
                ScreenshotService.stop()
                return@launchTry
            }
            if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.notification)) {
                return@launchTry
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
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = { Text(text = "快照设置") },
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
                text = "生成方式",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!AndroidTarget.R) {
                TextSwitch(
                    title = "截屏服务",
                    subtitle = "生成快照需要获取屏幕截图",
                    checked = screenshotServiceRunning,
                    onCheckedChange = ::setScreenshotServiceEnabled,
                )
            }
            TextSwitch(
                title = "音量快照",
                subtitle = "音量变化时保存快照",
                checked = store.captureVolumeChange,
                onCheckedChange = vm::setCaptureVolumeChange,
            )
            TextSwitch(
                title = "截屏快照",
                subtitle = "截屏时保存快照",
                checked = store.captureScreenshot,
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "打开配置截屏快照弹窗",
                        onClick = throttle { showCaptureScreenshotDialog = true },
                        id = R.drawable.ic_page_info,
                        contentDescription = "截屏快照设置",
                    )
                },
                onCheckedChange = vm::setCaptureScreenshot,
            )

            Text(
                text = "截图处理",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "隐藏状态栏",
                subtitle = "隐藏快照截图状态栏",
                checked = store.hideSnapshotStatusBar,
                onCheckedChange = vm::setHideSnapshotStatusBar,
            )

            Text(
                text = "导出",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "自动保存至下载",
                subtitle = "快照完成后导出 ZIP 文件",
                checked = store.autoSaveSnapshotToDownloads,
                onCheckedChange = scope.launchAsFn { enabled ->
                    if (
                        !enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.writeExternalStorage,
                        )
                    ) {
                        vm.setAutoSaveSnapshotToDownloads(enabled)
                    }
                },
            )
            Spacer(modifier = Modifier.height(EmptyHeight))
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
    AppAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "截屏快照")
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    onClick = throttle(onOpenHelp),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CustomOutlinedTextField(
                    label = { Text("应用ID") },
                    value = appIdValue,
                    placeholder = { Text(text = "请输入目标应用ID") },
                    onValueChange = { appIdValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                CustomOutlinedTextField(
                    label = { Text("特征事件选择器") },
                    value = eventSelectorValue,
                    placeholder = { Text(text = "请输入特征事件选择器") },
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
                Text(text = "确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "取消")
            }
        },
    )
}
