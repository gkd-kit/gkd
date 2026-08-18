package li.songe.gkd.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.app
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.service.ActivityService
import li.songe.gkd.service.ButtonService
import li.songe.gkd.service.EventService
import li.songe.gkd.service.HttpService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.CustomOutlinedTextField
import li.songe.gkd.ui.component.AppAlertDialog
import li.songe.gkd.ui.component.PerfCustomIconButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.SettingsDialog
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle

@Serializable
data object AdvancedPageRoute : NavKey

@Composable
fun AdvancedPage() {
    AdvancedContent()
}

@Composable
private fun AdvancedContent() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AdvancedVm>()
    val scope = vm.scope
    val showEditPortDialog by vm.showEditPortDialogFlow.collectAsStateWithLifecycle()
    val showCaptureScreenshotDialog by vm.showCaptureScreenshotDialogFlow.collectAsStateWithLifecycle()
    val showHttpSettingsDialog by vm.httpSettingsDialogVisibleFlow.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val httpServer by HttpService.httpServerFlow.collectAsStateWithLifecycle()
    val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsStateWithLifecycle()
    val screenshotServiceRunning by ScreenshotService.isRunning.collectAsStateWithLifecycle()
    val buttonServiceRunning by ButtonService.isRunning.collectAsStateWithLifecycle()
    val activityServiceRunning by ActivityService.isRunning.collectAsStateWithLifecycle()
    val eventServiceRunning by EventService.isRunning.collectAsStateWithLifecycle()

    if (showHttpSettingsDialog) {
        SettingsDialog(
            title = "HTTP 设置",
            onDismissRequest = { vm.setHttpSettingsDialogVisible(false) },
        ) {
            SettingItem(
                title = "服务端口",
                subtitle = store.httpServerPort.toString(),
                imageVector = PerfIcon.Edit,
                onClickLabel = "编辑服务端口",
                onClick = {
                    vm.setEditPortDialogVisible(true)
                },
            )
            TextSwitch(
                title = "清除订阅",
                subtitle = "关闭服务时删除内存订阅",
                checked = store.autoClearMemorySubs,
                onCheckedChange = vm::setAutoClearMemorySubs,
            )
        }
    }

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

    if (showEditPortDialog) {
        EditHttpPortDialog(
            currentPort = store.httpServerPort,
            onDismissRequest = { vm.setEditPortDialogVisible(false) },
            onConfirm = {
                if (vm.saveHttpServerPort(it)) {
                    vm.setEditPortDialogVisible(false)
                }
            },
        )
    }

    if (showCaptureScreenshotDialog) {
        CaptureScreenshotConfigDialog(
            appId = store.screenshotTargetAppId,
            eventSelector = store.screenshotEventSelector,
            onOpenHelp = {
                vm.setCaptureScreenshotDialogVisible(false)
                mainVm.navigateWebPage(ShortUrlSet.URL15)
            },
            onDismissRequest = { vm.setCaptureScreenshotDialogVisible(false) },
            onConfirm = { appId, selector ->
                if (vm.saveCaptureScreenshotConfig(appId, selector)) {
                    vm.setCaptureScreenshotDialogVisible(false)
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
                title = { Text(text = "高级设置") },
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
                text = "HTTP",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "HTTP服务",
                subtitle = "在浏览器下连接调试",
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "打开HTTP设置弹窗",
                        onClick = { vm.setHttpSettingsDialogVisible(true) },
                        id = R.drawable.ic_page_info,
                        contentDescription = "HTTP设置",
                        tint = if (showHttpSettingsDialog) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                },
                checked = httpServer != null,
                onCheckedChange = throttle(fn = scope.launchAsFn { enabled ->
                    HttpService.setEnabled(mainVm, enabled)
                }),
            )
            AnimatedVisibility(visible = httpServer != null) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    Column(modifier = Modifier.itemPadding()) {
                        Text(text = "点击下方链接即可连接")
                        Row {
                            val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                            Text(
                                text = localUrl,
                                color = MaterialTheme.colorScheme.primary,
                                style = LocalTextStyle.current.copy(
                                    textDecoration = TextDecoration.Underline
                                ),
                                modifier = Modifier.clickable(
                                    onClick = throttle { mainVm.openUrl(localUrl) }
                                ),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = "仅本设备访问")
                        }
                        localNetworkIps.forEach { host ->
                            val lanUrl = "http://${host}:${store.httpServerPort}"
                            Text(
                                text = lanUrl,
                                color = MaterialTheme.colorScheme.primary,
                                style = LocalTextStyle.current.copy(
                                    textDecoration = TextDecoration.Underline
                                ),
                                modifier = Modifier.clickable(
                                    onClick = throttle { mainVm.openUrl(lanUrl) }
                                ),
                            )
                        }
                    }
                }
            }
            Text(
                text = "快照",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "快照记录",
                subtitle = "应用界面节点信息及截图",
                onClick = { mainVm.navigatePage(SnapshotPageRoute) },
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
                title = "快照按钮",
                subtitle = "显示按钮点击保存快照",
                checked = buttonServiceRunning,
                onCheckedChange = scope.launchAsFn { enabled ->
                    ButtonService.setEnabled(mainVm, enabled)
                },
            )
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
                        onClick = throttle { vm.setCaptureScreenshotDialogVisible(true) },
                        id = R.drawable.ic_page_info,
                        contentDescription = "截屏快照设置",
                    )
                },
                onCheckedChange = vm::setCaptureScreenshot,
            )
            TextSwitch(
                title = "隐藏状态栏",
                subtitle = "隐藏快照截图状态栏",
                checked = store.hideSnapshotStatusBar,
                onCheckedChange = vm::setHideSnapshotStatusBar,
            )
            TextSwitch(
                title = "保存提示",
                subtitle = "提示「正在保存快照」",
                checked = store.showSaveSnapshotToast,
                onCheckedChange = vm::setShowSaveSnapshotToast,
            )
            SettingItem(
                title = "Github Cookie",
                subtitle = "生成快照/日志链接",
                suffix = "获取教程",
                suffixUnderline = true,
                onSuffixClick = mainVm.githubUpload::openCookieHelp,
                imageVector = PerfIcon.Edit,
                onClick = mainVm.githubUpload::editCookie,
            )

            Text(
                text = "日志",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "界面日志",
                subtitle = "界面切换日志",
                onClick = { mainVm.navigatePage(ActivityLogRoute) },
            )
            TextSwitch(
                title = "界面服务",
                subtitle = "显示当前界面信息",
                checked = activityServiceRunning,
                onCheckedChange = scope.launchAsFn { enabled ->
                    ActivityService.setEnabled(mainVm, enabled)
                },
            )
            SettingItem(
                title = "事件日志",
                subtitle = "无障碍事件日志",
                onClick = { mainVm.navigatePage(A11yEventLogRoute) },
            )
            TextSwitch(
                title = "事件服务",
                subtitle = "显示无障碍事件",
                checked = eventServiceRunning,
                onCheckedChange = scope.launchAsFn { enabled ->
                    EventService.setEnabled(mainVm, enabled)
                },
            )
            Spacer(modifier = Modifier.height(EmptyHeight))
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
    AppAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = "服务端口") },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(text = "请输入 1000-65535 的整数") },
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = "${value.length} / 5",
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
