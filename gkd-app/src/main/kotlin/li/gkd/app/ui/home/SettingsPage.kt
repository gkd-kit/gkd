package li.gkd.app.ui.home

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.service.StatusService
import li.gkd.app.service.TrackService
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.AboutRoute
import li.gkd.app.ui.AdvancedPageRoute
import li.gkd.app.ui.BlockA11yAppListRoute
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.component.CustomOutlinedTextField
import li.gkd.app.ui.component.FullscreenDialog
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.PerfCustomIconButton
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SettingItem
import li.gkd.app.ui.component.SettingsDialog
import li.gkd.app.ui.component.TextListDialog
import li.gkd.app.ui.component.TextMenu
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.DarkThemeOption
import li.gkd.app.util.findOption
import li.gkd.app.util.launchTry
import li.gkd.app.util.openAppDetailsSettings
import li.gkd.app.util.throttle
import li.gkd.app.util.toast
import kotlin.time.Duration.Companion.milliseconds

private const val ZIP_MIME_TYPE = "application/zip"

@Composable
fun useSettingsPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<SettingsVm>()
    val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
    val trackServiceRunning by TrackService.isRunning.collectAsStateWithLifecycle()
    val privilegeAvailable = privilegeContextFlow.collectAsStateWithLifecycle().value != null
    val store by storeFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    val showToastInputDlg by vm.showActionToastDialogFlow.collectAsStateWithLifecycle()
    val showNotifTextInputDlg by vm.showNotificationTextDialogFlow.collectAsStateWithLifecycle()
    val showA11yBlockDlg by vm.showA11yBlockDialogFlow.collectAsStateWithLifecycle()
    val showBackupDialog by vm.showBackupDialogFlow.collectAsStateWithLifecycle()
    val showExportBackupDialog by vm.showExportBackupDialogFlow.collectAsStateWithLifecycle()
    val showToastSettingsDialog by vm.toastSettingsDialogVisibleFlow.collectAsStateWithLifecycle()

    if (showToastSettingsDialog) {
        SettingsDialog(
            title = "提示设置",
            onDismissRequest = { vm.setToastSettingsDialogVisible(false) },
        ) {
            TextSwitch(
                title = "提示样式",
                subtitle = "使用系统样式",
                suffix = "查看限制",
                onSuffixClick = {
                    actionScope.launchTry {
                        mainVm.dialogRequests.showMessage(
                            title = "限制说明",
                            text = "系统 Toast 存在频率限制, 触发过于频繁会被系统强制不显示\n\n如果只使用开屏一类低频率规则可使用系统提示, 否则建议关闭此项使用自定义样式提示",
                        )
                    }
                },
                checked = store.useSystemToast,
                onCheckedChange = vm::setUseSystemToast,
            )
            TextSwitch(
                title = "轨迹提示",
                subtitle = "显示触发位置信息",
                checked = trackServiceRunning,
                onCheckedChange = { enabled ->
                    actionScope.launchTry {
                        if (enabled) {
                            if (!mainVm.dialogRequests.confirm(
                                title = "使用须知",
                                text = "开启「轨迹提示」后点击或滑动后会在屏幕上使用悬浮窗绘制轨迹(一段时间后消失)，如果新触摸事件恰好在悬浮窗区域内，可能会被目标应用拒绝，从而导致点击或滑动无响应",
                                confirmText = "继续",
                            )) return@launchTry
                            if (
                                !mainVm.permissionRequests.ensurePermissions(
                                    PermissionStates.foregroundServiceSpecialUse,
                                    PermissionStates.notification,
                                    PermissionStates.drawOverlays,
                                )
                            ) {
                                return@launchTry
                            }
                        }
                        vm.setTrackServiceEnabled(enabled)
                    }
                },
            )
        }
    }

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.actionToast)
        }
        val maxCharLen = 64
        AppAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "触发提示")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "文案规则",
                        onClickLabel = "打开文案规则弹窗",
                        onClick = throttle {
                            actionScope.launchTry {
                                mainVm.dialogRequests.showMessage(
                                    title = "文案规则",
                                    text = $$"触发文案支持变量替换，规则如下\n${1} 子规则名称\n${2} 规则名称\n${3} 触发次数\n\n示例模板\n${1}/${2}/${3}\n\n替换结果\n子规则a/规则A/3",
                                )
                            }
                        },
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "请输入提示内容")
                    },
                    onValueChange = {
                        value = it.take(maxCharLen)
                    },
                    supportingText = {
                        Text(
                            text = "${value.length} / $maxCharLen",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus()
                )
            },
            onDismissRequest = { vm.setActionToastDialogVisible(false) },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    if (vm.saveActionToast(value)) {
                        toast("更新成功")
                    }
                    vm.setActionToastDialogVisible(false)
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setActionToastDialogVisible(false) }) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showNotifTextInputDlg) {
        var titleValue by remember { mutableStateOf(store.customNotifTitle) }
        var textValue by remember { mutableStateOf(store.customNotifText) }
        AppAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "通知文案")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "文案规则",
                        onClickLabel = "打开文案规则弹窗",
                        onClick = throttle {
                            actionScope.launchTry {
                                mainVm.dialogRequests.showMessage(
                                    title = "文案规则",
                                    text = $$"通知文案支持变量替换，规则如下\n${i} 全局规则数\n${k} 应用数\n${u} 应用规则数\n${n} 触发次数\n\n示例模板\n${i}全局/${k}应用/${u}规则/${n}触发\n\n替换结果\n0全局/1应用/2规则/3触发",
                                )
                            }
                        },
                    )
                }
            },
            text = {
                val titleMaxLen = 32
                val textMaxLen = 64
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CustomOutlinedTextField(
                        label = { Text("主标题") },
                        value = titleValue,
                        placeholder = { Text(text = "请输入内容，支持变量替换") },
                        onValueChange = {
                            titleValue = (if (it.length > titleMaxLen) it.take(titleMaxLen) else it)
                                .filter { c -> c !in "\n\r" }
                        },
                        supportingText = {
                            Text(
                                text = "${titleValue.length} / $titleMaxLen",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CustomOutlinedTextField(
                        label = { Text("副标题") },
                        value = textValue,
                        placeholder = { Text(text = "请输入内容，支持变量替换") },
                        onValueChange = {
                            textValue = if (it.length > textMaxLen) it.take(textMaxLen) else it
                        },
                        supportingText = {
                            Text(
                                text = "${textValue.length} / $textMaxLen",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        contentPadding = PaddingValues(12.dp),
                    )
                }
            },
            onDismissRequest = {
                vm.setNotificationTextDialogVisible(false)
            },
            confirmButton = {
                TextButton(onClick = {
                    context.imeController.requestHide()
                    if (vm.saveNotificationText(titleValue, textValue)) {
                        toast("更新成功")
                    }
                    vm.setNotificationTextDialogVisible(false)
                }) {
                    Text(
                        text = "确认",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setNotificationTextDialogVisible(false) }) {
                    Text(
                        text = "取消",
                    )
                }
            })
    }


    if (showA11yBlockDlg) {
        BlockA11yDialog(
            onDismissRequest = { vm.setA11yBlockDialogVisible(false) },
        )
    }
    if (showBackupDialog) {
        TextListDialog(
            onDismiss = { vm.setBackupDialogVisible(false) },
            textList = listOf(
                "导入备份" to {
                    actionScope.launchTry {
                        val uri = mainVm.activityResults.openDocument(ZIP_MIME_TYPE)
                        if (uri == null) {
                            toast("未选择文件")
                            return@launchTry
                        }
                        vm.importBackup(uri)
                    }
                },
                "导出备份" to {
                    vm.setExportBackupDialogVisible(true)
                },
            )
        )
    }
    if (showExportBackupDialog) {
        TextListDialog(
            onDismiss = { vm.setExportBackupDialogVisible(false) },
            textList = listOf(
                "分享到其他应用" to {
                    actionScope.launchTry {
                        val file = vm.exportBackup()
                        context.shareFile(file, "分享备份文件")
                    }
                },
                "保存到下载" to {
                    actionScope.launchTry {
                        val file = vm.exportBackup()
                        context.saveFileToDownloads(file)
                    }
                },
            )
        )
    }

    val pageScrollState = rememberColumnScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val scrollState = pageScrollState.scrollState
    ResetPageScrollOnRequest(BottomNavItem.Settings, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = BottomNavItem.Settings.label,
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = "常规",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "触发提示",
                subtitle = store.actionToast,
                checked = store.toastWhenClick,
                onClickLabel = "打开触发提示弹窗",
                onClick = {
                    vm.setActionToastDialogVisible(true)
                },
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "打开提示设置弹窗",
                        onClick = { vm.setToastSettingsDialogVisible(true) },
                        id = R.drawable.ic_page_info,
                        contentDescription = "提示设置",
                        tint = if (showToastSettingsDialog) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                },
                onCheckedChange = {
                    vm.setToastWhenClick(it)
                })

            TextSwitch(
                title = "通知文案",
                subtitle = if (store.useCustomNotifText) {
                    store.customNotifTitle + " / " + store.customNotifText
                } else {
                    subsStatus
                },
                checked = store.useCustomNotifText,
                onClickLabel = "打开修改通知文案弹窗",
                onClick = { vm.setNotificationTextDialogVisible(true) },
                onCheckedChange = {
                    vm.setUseCustomNotificationText(it)
                })

            TextSwitch(
                title = "后台隐藏",
                subtitle = "在「最近任务」隐藏卡片",
                checked = store.excludeFromRecents,
                onCheckedChange = { enabled ->
                    actionScope.launchTry {
                        if (enabled) {
                            if (!mainVm.dialogRequests.confirm(
                                title = "后台隐藏",
                                text = "隐藏卡片后可能导致部分设备无法给任务卡片加锁后台，建议先加锁后再隐藏，若已加锁或没有锁后台机制请继续",
                                confirmText = "继续",
                            )) return@launchTry
                        }
                        vm.setExcludeFromRecents(enabled)
                    }
                })

            var blockSectionVisible by remember {
                mutableStateOf(store.enableBlockA11yAppList)
            }
            LaunchedEffect(store.enableBlockA11yAppList) {
                delay(300.milliseconds)
                blockSectionVisible = store.enableBlockA11yAppList
            }
            AnimatedVisibility(visible = blockSectionVisible) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleItemPadding(),
                    text = "无障碍",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextSwitch(
                title = "局部关闭",
                subtitle = "白名单内关闭服务",
                checked = store.enableBlockA11yAppList && privilegeAvailable,
                onCheckedChange = {
                    if (it && !privilegeAvailable) {
                        mainVm.navigatePage(PrivilegeServiceRoute)
                    } else if (it) {
                        vm.setA11yBlockDialogVisible(true)
                    } else {
                        vm.setBlockA11yAppListEnabled(false)
                    }
                },
            )
            AnimatedVisibility(visible = blockSectionVisible) {
                SettingItem(title = "白名单", onClickLabel = "进入无障碍白名单页面", onClick = {
                    mainVm.navigatePage(BlockA11yAppListRoute)
                })
            }

            Text(
                text = "外观",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextMenu(
                title = "深色模式",
                option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
                onOptionChange = {
                    vm.setDarkTheme(it.value)
                }
            )

            if (AndroidTarget.S) {
                TextSwitch(
                    title = "动态配色",
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        vm.setDynamicColor(it)
                    }
                )
            }

            Text(
                text = "其他",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = "高级设置", onClick = {
                mainVm.navigatePage(AdvancedPageRoute)
            })
            SettingItem(title = "备份恢复", onClick = {
                vm.setBackupDialogVisible(true)
            })

            SettingItem(title = "关于", onClick = {
                mainVm.navigatePage(AboutRoute)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun BlockA11yDialog(
    onDismissRequest: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SettingsVm>()
    val statusRunning by StatusService.isRunning.collectAsStateWithLifecycle()
    val privilegeContext by privilegeContextFlow.collectAsStateWithLifecycle()
    val ignoreBatteryOptimizations by PermissionStates.ignoreBatteryOptimizations.stateFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    val scrollState = rememberScrollState()
    FullscreenDialog(onDismissRequest) {
        Scaffold(
            topBar = {
                PerfTopAppBar(
                    navigationIcon = {
                        PerfIconButton(
                            imageVector = PerfIcon.Close,
                            onClickLabel = "关闭弹窗",
                            onClick = onDismissRequest,
                        )
                    },
                    title = {
                        Text(text = "局部关闭")
                    },
                )
            },
            bottomBar = {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = privilegeContext != null && statusRunning && ignoreBatteryOptimizations,
                        onClick = {
                            actionScope.launchTry {
                                onDismissRequest()
                                delay(200.milliseconds)
                                vm.setBlockA11yAppListEnabled(true)
                            }
                        }
                    ) {
                        Text(text = "继续")
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                }
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(horizontal = itemHorizontalPadding)
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    Text(text = "「局部关闭」可在白名单应用内关闭服务，来解决界面异常，游戏掉帧或无障碍检测的问题")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "使用须知", style = MaterialTheme.typography.titleMedium)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RequiredTextItem(text = "切换服务会造成短暂触摸卡顿，请自行测试后再编辑白名单")
                        RequiredTextItem(text = "使用其它无障碍应用可能导致优化无效，可在服务关闭后自行确认")
                        RequiredTextItem(text = "必须确保服务关闭后的持续后台运行，否则会被系统暂停或结束运行导致重启失败")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "使用条件", style = MaterialTheme.typography.titleMedium)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RequiredTextItem(
                            text = "特权服务",
                            enabled = privilegeContext == null,
                            imageVector = if (privilegeContext != null) PerfIcon.Check else PerfIcon.ArrowForward,
                            onClick = {
                                mainVm.navigatePage(PrivilegeServiceRoute)
                            },
                        )
                        RequiredTextItem(
                            text = "开启「常驻通知」",
                            enabled = !statusRunning,
                            imageVector = if (statusRunning) PerfIcon.Check else PerfIcon.ArrowForward,
                            onClick = {
                                actionScope.launchTry {
                                    StatusService.requestStart(mainVm)
                                }
                            },
                        )
                        RequiredTextItem(
                            text = "省电策略设置为无限制",
                            enabled = !ignoreBatteryOptimizations,
                            imageVector = if (ignoreBatteryOptimizations) PerfIcon.Check else PerfIcon.ArrowForward,
                            onClickLabel = "打开忽略电池优化设置页面",
                            onClick = {
                                actionScope.launchTry {
                                    mainVm.permissionRequests.ensurePermissions(
                                        PermissionStates.ignoreBatteryOptimizations,
                                    )
                                }
                            },
                        )
                        RequiredTextItem(
                            text = "(可选) 允许自启动",
                            enabled = true,
                            imageVector = PerfIcon.OpenInNew,
                            onClickLabel = "打开应用详情页面",
                            onClick = {
                                openAppDetailsSettings()
                            },
                        )
                        RequiredTextItem(
                            text = "(可选) 在「最近任务」锁定",
                            enabled = true,
                            imageVector = PerfIcon.OpenInNew,
                            onClickLabel = "打开应用详情页面",
                            onClick = {
                                val inputManager = privilegeContextFlow.value?.inputManager
                                if (inputManager == null) {
                                    mainVm.navigatePage(PrivilegeServiceRoute)
                                } else {
                                    inputManager.keyevent(KeyEvent.KEYCODE_APP_SWITCH)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "某些场景下服务刚启动时概率不工作，如多次遇到此情况则不建议使用此功能")
                }
                Spacer(modifier = Modifier.height(EmptyHeight))
            }
        }
    }
}

@Composable
private fun RequiredTextItem(
    text: String,
    imageVector: ImageVector? = null,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .run {
                if (onClick != null) {
                    clickable(
                        enabled = enabled,
                        onClick = throttle(onClick),
                        onClickLabel = onClickLabel
                    )
                } else {
                    this
                }
            }
            .padding(horizontal = 4.dp),
    ) {
        val lineHeightDp = LocalDensity.current.run { LocalTextStyle.current.lineHeight.toDp() }
        Spacer(
            modifier = Modifier
                .padding(vertical = (lineHeightDp - 4.dp) / 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .size(4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
        if (imageVector != null) {
            PerfIcon(
                imageVector = imageVector,
                modifier = Modifier.iconTextSize(),
            )
        }
    }

}
