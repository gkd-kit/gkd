package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.A11YEventLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.service.ActivityService
import li.songe.gkd.service.ButtonService
import li.songe.gkd.service.EventService
import li.songe.gkd.service.HttpService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.shizuku.updateBinderMutex
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.CustomIconButton
import li.songe.gkd.ui.component.CustomOutlinedTextField
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.iconTextSize
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.selector.Selector

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AdvancedPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AdvancedVm>()
    val store by storeFlow.collectAsState()

    var showEditPortDlg by vm.showEditPortDlgFlow.asMutableState()
    if (showEditPortDlg) {
        val portRange = remember { 1000 to 65535 }
        val placeholderText = remember { "请输入 ${portRange.first}-${portRange.second} 的整数" }
        var value by remember {
            mutableStateOf(store.httpServerPort.toString())
        }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(text = "服务端口") },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = placeholderText)
                    },
                    onValueChange = {
                        value = it.filter { c -> c.isDigit() }.take(5)
                    },
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
            onDismissRequest = {
                showEditPortDlg = false
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotEmpty(),
                    onClick = {
                        val newPort = value.toIntOrNull()
                        if (newPort == null || !(portRange.first <= newPort && newPort <= portRange.second)) {
                            toast(placeholderText)
                            return@TextButton
                        }
                        showEditPortDlg = false
                        if (newPort != store.httpServerPort) {
                            storeFlow.value = store.copy(
                                httpServerPort = newPort
                            )
                            toast("更新成功")
                        }
                    }
                ) {
                    Text(
                        text = "确认", modifier = Modifier
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPortDlg = false }) {
                    Text(
                        text = "取消"
                    )
                }
            }
        )
    }

    var showShizukuState by vm.showShizukuStateFlow.asMutableState()
    if (showShizukuState) {
        val onDismissRequest = { showShizukuState = false }
        AlertDialog(
            title = { Text(text = "授权状态") },
            text = {
                val states = shizukuContextFlow.collectAsState().value.states
                Column {
                    states.forEach { (name, value) ->
                        Text(
                            text = name,
                            textDecoration = if (value != null) null else TextDecoration.LineThrough,
                        )
                    }
                }
            },
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = "我知道了")
                }
            },
        )
    }

    var showCaptureScreenshotDlg by vm.showCaptureScreenshotDlgFlow.asMutableState()
    if (showCaptureScreenshotDlg) {
        var appIdValue by remember { mutableStateOf(store.screenshotTargetAppId) }
        var eventSelectorValue by remember { mutableStateOf(store.screenshotEventSelector) }
        AlertDialog(
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
                        onClick = throttle {
                            showCaptureScreenshotDlg = false
                            mainVm.navigateWebPage(ShortUrlSet.URL15)
                        },
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CustomOutlinedTextField(
                        label = { Text("应用ID") },
                        value = appIdValue,
                        placeholder = { Text(text = "请输入目标应用ID") },
                        onValueChange = {
                            appIdValue = it
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CustomOutlinedTextField(
                        label = { Text("特征事件选择器") },
                        value = eventSelectorValue,
                        placeholder = { Text(text = "请输入特征事件选择器") },
                        onValueChange = {
                            eventSelectorValue = it
                        },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                    )
                }
            },
            onDismissRequest = {
                showCaptureScreenshotDlg = false
            },
            confirmButton = {
                TextButton(onClick = throttle {
                    if (appIdValue == store.screenshotTargetAppId && eventSelectorValue == store.screenshotEventSelector) {
                        showCaptureScreenshotDlg = false
                        return@throttle
                    }
                    if (appIdValue.isNotEmpty() && !appInfoMapFlow.value.contains(appIdValue)) {
                        toast("无效应用ID")
                        return@throttle
                    }
                    if (eventSelectorValue.isNotEmpty()) {
                        val s = Selector.parseOrNull(eventSelectorValue)
                        if (s == null) {
                            toast("无效事件选择器")
                            return@throttle
                        }
                    }
                    storeFlow.update {
                        it.copy(
                            screenshotTargetAppId = appIdValue,
                            screenshotEventSelector = eventSelectorValue,
                        )
                    }
                    toast("更新成功")
                    showCaptureScreenshotDlg = false
                }) {
                    Text(
                        text = "确认",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCaptureScreenshotDlg = false }) {
                    Text(
                        text = "取消",
                    )
                }
            })
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                        mainVm.popBackStack()
                    })
                },
                title = { Text(text = "高级设置") },
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .titleItemPadding(showTop = false),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier,
                    text = "Shizuku",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                PerfIcon(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = throttle {
                            showShizukuState = true
                        })
                        .iconTextSize(textStyle = MaterialTheme.typography.titleSmall),
                    imageVector = PerfIcon.Api,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            val shizukuGranted by shizukuGrantedState.stateFlow.collectAsState()
            AnimatedVisibility(store.enableShizuku && !shizukuGranted) {
                AuthCard(
                    title = "未授权",
                    subtitle = "点击授权以优化体验",
                    onAuthClick = {
                        mainVm.requestShizuku()
                    }
                )
            }
            TextSwitch(
                title = "启用优化",
                subtitle = "提升权限优化体验",
                suffix = "了解更多",
                suffixUnderline = true,
                onSuffixClick = { mainVm.navigateWebPage(ShortUrlSet.URL14) },
                checked = store.enableShizuku,
                suffixIcon = {
                    if (updateBinderMutex.state.collectAsState().value) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp),
                        )
                    }
                },
            ) {
                mainVm.switchEnableShizuku(it)
            }

            val server by HttpService.httpServerFlow.collectAsState()
            val httpServerRunning = server != null
            val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsState()

            Text(
                text = "HTTP",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.itemPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HTTP服务",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium
                    ) {
                        Text(text = if (httpServerRunning) "点击链接打开即可自动连接" else "在浏览器下连接调试工具")
                        AnimatedVisibility(httpServerRunning) {
                            Column {
                                Row {
                                    val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                                    Text(
                                        text = localUrl,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                        modifier = Modifier.clickable(onClick = throttle {
                                            mainVm.openUrl(localUrl)
                                        }),
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(text = "仅本设备可访问")
                                }
                                localNetworkIps.forEach { host ->
                                    val lanUrl = "http://${host}:${store.httpServerPort}"
                                    Text(
                                        text = lanUrl,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                        modifier = Modifier.clickable(onClick = throttle {
                                            mainVm.openUrl(lanUrl)
                                        })
                                    )
                                }
                            }
                        }
                    }
                }
                Switch(
                    checked = httpServerRunning,
                    onCheckedChange = throttle(fn = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, foregroundServiceSpecialUseState)
                            requiredPermission(context, notificationState)
                            HttpService.start()
                        } else {
                            HttpService.stop()
                        }
                    })
                )
            }

            SettingItem(
                title = "服务端口",
                subtitle = store.httpServerPort.toString(),
                imageVector = PerfIcon.Edit,
                onClick = {
                    showEditPortDlg = true
                }
            )

            TextSwitch(
                title = "清除订阅",
                subtitle = "关闭服务时删除内存订阅",
                checked = store.autoClearMemorySubs
            ) {
                storeFlow.value = store.copy(
                    autoClearMemorySubs = it
                )
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
                onClick = {
                    mainVm.navigatePage(SnapshotPageDestination)
                }
            )

            if (!AndroidTarget.R) {
                val screenshotRunning by ScreenshotService.isRunning.collectAsState()
                TextSwitch(
                    title = "截屏服务",
                    subtitle = "生成快照需要获取屏幕截图",
                    checked = screenshotRunning,
                    onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, notificationState)
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val activityResult =
                                context.launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                            if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                                ScreenshotService.start(intent = activityResult.data!!)
                            }
                        } else {
                            ScreenshotService.stop()
                        }
                    }
                )
            }

            TextSwitch(
                title = "快照按钮",
                subtitle = "显示按钮点击保存快照",
                checked = ButtonService.isRunning.collectAsState().value,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, foregroundServiceSpecialUseState)
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        ButtonService.start()
                    } else {
                        ButtonService.stop()
                    }
                }
            )

            TextSwitch(
                title = "音量快照",
                subtitle = "音量变化时保存快照",
                checked = store.captureVolumeChange
            ) {
                storeFlow.value = store.copy(
                    captureVolumeChange = it
                )
            }

            TextSwitch(
                title = "截屏快照",
                subtitle = "截屏时保存快照",
                checked = store.captureScreenshot,
                suffixIcon = {
                    CustomIconButton(
                        size = 32.dp,
                        onClick = throttle {
                            showCaptureScreenshotDlg = true
                        },
                    ) {
                        PerfIcon(
                            modifier = Modifier.size(20.dp),
                            id = SafeR.ic_page_info,
                        )
                    }
                },
            ) {
                storeFlow.value = store.copy(
                    captureScreenshot = it
                )
                if (it && store.screenshotTargetAppId.isEmpty() || store.screenshotEventSelector.isEmpty()) {
                    toast("请配置目标应用和特征事件选择器")
                }
            }

            TextSwitch(
                title = "隐藏状态栏",
                subtitle = "隐藏快照截图状态栏",
                checked = store.hideSnapshotStatusBar
            ) {
                storeFlow.value = store.copy(
                    hideSnapshotStatusBar = it
                )
            }

            TextSwitch(
                title = "保存提示",
                subtitle = "提示「正在保存快照」",
                checked = store.showSaveSnapshotToast
            ) {
                storeFlow.value = store.copy(
                    showSaveSnapshotToast = it
                )
            }

            SettingItem(
                title = "Github Cookie",
                subtitle = "生成快照/日志链接",
                suffix = "获取教程",
                suffixUnderline = true,
                onSuffixClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL1)
                },
                imageVector = PerfIcon.Edit,
                onClick = {
                    mainVm.showEditCookieDlgFlow.value = true
                }
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
                onClick = {
                    mainVm.navigatePage(ActivityLogPageDestination)
                }
            )
            TextSwitch(
                title = "界面服务",
                subtitle = "显示当前界面信息",
                checked = ActivityService.isRunning.collectAsState().value,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, foregroundServiceSpecialUseState)
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        ActivityService.start()
                    } else {
                        ActivityService.stop()
                    }
                }
            )
            SettingItem(
                title = "事件日志",
                subtitle = "无障碍事件日志",
                onClick = {
                    mainVm.navigatePage(A11YEventLogPageDestination)
                }
            )
            TextSwitch(
                title = "事件服务",
                subtitle = "显示无障碍事件",
                checked = EventService.isRunning.collectAsState().value,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, foregroundServiceSpecialUseState)
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        EventService.start()
                    } else {
                        EventService.stop()
                    }
                }
            )

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
