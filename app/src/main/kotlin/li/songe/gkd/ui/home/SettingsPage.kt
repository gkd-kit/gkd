package li.songe.gkd.ui.home

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.AboutPageDestination
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.generated.destinations.BlockA11YAppListPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.app
import li.songe.gkd.permission.ignoreBatteryOptimizationsState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.permission.writeSecureSettingsState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.StatusService
import li.songe.gkd.service.fixRestartAutomatorService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.CustomOutlinedTextField
import li.songe.gkd.ui.component.FullscreenDialog
import li.songe.gkd.ui.component.PerfCustomIconButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextMenu
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.iconTextSize
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.DarkThemeOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.mapState
import li.songe.gkd.util.openA11ySettings
import li.songe.gkd.util.openAppDetailsSettings
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Composable
fun useSettingsPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val store by storeFlow.collectAsState()
    val vm = viewModel<HomeVm>()

    var showToastInputDlg by vm.showToastInputDlgFlow.asMutableState()

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.actionToast)
        }
        val maxCharLen = 32
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(text = "触发提示") },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "请输入提示内容")
                    },
                    onValueChange = {
                        value = it.take(maxCharLen)
                    },
                    singleLine = true,
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
            onDismissRequest = { showToastInputDlg = false },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    if (value != storeFlow.value.actionToast) {
                        storeFlow.update { it.copy(actionToast = value) }
                        toast("更新成功")
                    }
                    showToastInputDlg = false
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showToastInputDlg = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    var showNotifTextInputDlg by vm.showNotifTextInputDlgFlow.asMutableState()
    if (showNotifTextInputDlg) {
        var titleValue by remember { mutableStateOf(store.customNotifTitle) }
        var textValue by remember { mutableStateOf(store.customNotifText) }
        AlertDialog(
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
                            showNotifTextInputDlg = false
                            val confirmAction = {
                                mainVm.dialogFlow.value = null
                                showNotifTextInputDlg = true
                            }
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "文案规则",
                                text = $$"通知文案支持变量替换，规则如下\n${i} 全局规则数\n${k} 应用数\n${u} 应用规则组数\n${n} 触发次数\n\n示例模板\n${i}全局/${k}应用/${u}规则组/${n}触发\n\n替换结果\n0全局/1应用/2规则组/3触发",
                                confirmAction = confirmAction,
                                onDismissRequest = confirmAction,
                            )
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
                showNotifTextInputDlg = false
            },
            confirmButton = {
                TextButton(onClick = {
                    context.justHideSoftInput()
                    if (store.customNotifTitle != textValue || store.customNotifText != textValue) {
                        storeFlow.update {
                            it.copy(
                                customNotifTitle = titleValue,
                                customNotifText = textValue
                            )
                        }
                        toast("更新成功")
                    }
                    showNotifTextInputDlg = false
                }) {
                    Text(
                        text = "确认",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifTextInputDlg = false }) {
                    Text(
                        text = "取消",
                    )
                }
            })
    }

    var showToastSettingsDlg by vm.showToastSettingsDlgFlow.asMutableState()
    if (showToastSettingsDlg) {
        AlertDialog(
            onDismissRequest = { showToastSettingsDlg = false },
            title = { Text("提示设置") },
            text = {
                TextSwitch(
                    paddingDisabled = true,
                    title = "系统提示",
                    subtitle = "系统样式触发提示",
                    suffix = "查看限制",
                    onSuffixClick = {
                        showToastSettingsDlg = false
                        val confirmAction = {
                            mainVm.dialogFlow.value = null
                            showToastSettingsDlg = true
                        }
                        mainVm.dialogFlow.updateDialogOptions(
                            title = "限制说明",
                            text = "系统 Toast 存在频率限制, 触发过于频繁会被系统强制不显示\n\n如果只使用开屏一类低频率规则可使用系统提示, 否则建议关闭此项使用自定义样式提示",
                            confirmAction = confirmAction,
                            onDismissRequest = confirmAction,
                        )
                    },
                    checked = store.useSystemToast,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            useSystemToast = it
                        )
                    })
            },
            confirmButton = {
                TextButton(onClick = { showToastSettingsDlg = false }) {
                    Text("关闭")
                }
            }
        )
    }

    var showA11yBlockDlg by vm.showA11yBlockDlgFlow.asMutableState()
    if (showA11yBlockDlg) {
        BlockA11yDialog(onDismissRequest = { showA11yBlockDlg = false })
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
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
                    showToastInputDlg = true
                },
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "打开提示设置弹窗",
                        onClick = throttle { showToastSettingsDlg = true },
                        id = R.drawable.ic_page_info,
                        contentDescription = "提示设置",
                    )
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            val subsStatus by vm.subsStatusFlow.collectAsState()
            TextSwitch(
                title = "通知文案",
                subtitle = if (store.useCustomNotifText) {
                    store.customNotifTitle + " / " + store.customNotifText
                } else {
                    subsStatus
                },
                checked = store.useCustomNotifText,
                onClickLabel = "打开修改通知文案弹窗",
                onClick = { showNotifTextInputDlg = true },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        useCustomNotifText = it
                    )
                })

            TextSwitch(
                title = "后台隐藏",
                subtitle = "在「最近任务」隐藏卡片",
                checked = store.excludeFromRecents,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        mainVm.dialogFlow.waitResult(
                            title = "后台隐藏",
                            text = "隐藏卡片后可能导致部分设备无法给任务卡片加锁后台，建议先加锁后再隐藏，若已加锁或没有锁后台机制请继续",
                            confirmText = "继续",
                        )
                    }
                    storeFlow.value = store.copy(
                        excludeFromRecents = !store.excludeFromRecents
                    )
                })

            val scope = rememberCoroutineScope()
            val lazyOn = remember {
                storeFlow.mapState(scope) { it.enableBlockA11yAppList }.debounce(300)
                    .stateIn(scope, SharingStarted.Eagerly, store.enableBlockA11yAppList)
            }.collectAsState()
            AnimatedVisibility(visible = lazyOn.value) {
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
                checked = store.enableBlockA11yAppList && shizukuContextFlow.collectAsState().value.ok,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        showA11yBlockDlg = true
                    } else {
                        storeFlow.value = store.copy(enableBlockA11yAppList = false)
                        fixRestartAutomatorService()
                    }
                },
            )
            AnimatedVisibility(visible = lazyOn.value) {
                SettingItem(title = "白名单", onClickLabel = "进入无障碍白名单页面", onClick = {
                    mainVm.navigatePage(BlockA11YAppListPageDestination)
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
                    storeFlow.update { s -> s.copy(enableDarkTheme = it.value) }
                }
            )

            if (AndroidTarget.S) {
                TextSwitch(
                    title = "动态配色",
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        storeFlow.update { s -> s.copy(enableDynamicColor = it) }
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
                mainVm.navigatePage(AdvancedPageDestination)
            })

            SettingItem(title = "关于", onClick = {
                mainVm.navigatePage(AboutPageDestination)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun BlockA11yDialog(onDismissRequest: () -> Unit) = FullscreenDialog(onDismissRequest) {
    val mainVm = LocalMainViewModel.current
    val statusRunning by StatusService.isRunning.collectAsState()
    val shizukuContext by shizukuContextFlow.collectAsState()
    val ignoreBatteryOptimizations by ignoreBatteryOptimizationsState.stateFlow.collectAsState()
    val hasOtherA11y by mainVm.hasOtherA11yFlow.collectAsState()
    val context = LocalActivity.current as MainActivity
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
                    enabled = shizukuContext.ok && statusRunning && ignoreBatteryOptimizations && !hasOtherA11y,
                    onClick = mainVm.viewModelScope.launchAsFn {
                        onDismissRequest()
                        delay(200)
                        storeFlow.update { it.copy(enableBlockA11yAppList = true) }
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
                .verticalScroll(rememberScrollState())
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
                    RequiredTextItem(text = "使用其它无障碍应用会导致优化无效，因为无障碍不会被完全关闭")
                    RequiredTextItem(text = "必须确保服务关闭后的持续后台运行，否则会被系统暂停或结束运行导致重启失败")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "使用条件", style = MaterialTheme.typography.titleMedium)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RequiredTextItem(
                        text = "Shizuku 授权",
                        enabled = !shizukuContext.ok,
                        imageVector = if (shizukuContext.ok) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = mainVm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            mainVm.guardShizukuContext()
                        },
                    )
                    RequiredTextItem(
                        text = "开启「常驻通知」",
                        enabled = !statusRunning,
                        imageVector = if (statusRunning) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = mainVm.viewModelScope.launchAsFn {
                            StatusService.requestStart(context)
                        },
                    )
                    RequiredTextItem(
                        text = "省电策略设置为无限制",
                        enabled = !ignoreBatteryOptimizations,
                        imageVector = if (ignoreBatteryOptimizations) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClickLabel = "打开忽略电池优化设置页面",
                        onClick = mainVm.viewModelScope.launchAsFn {
                            requiredPermission(context, ignoreBatteryOptimizationsState)
                        },
                    )
                    RequiredTextItem(
                        text = "关闭其它应用的无障碍",
                        enabled = hasOtherA11y,
                        imageVector = if (!hasOtherA11y) PerfIcon.Check else PerfIcon.ArrowForward,
                        onClick = {
                            if (writeSecureSettingsState.updateAndGet()) {
                                if (A11yService.isRunning.value) {
                                    setOf(A11yService.a11yCn)
                                } else {
                                    emptySet()
                                }.let {
                                    app.putSecureA11yServices(it)
                                }
                                toast("关闭成功")
                            } else {
                                openA11ySettings()
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
                            val m = shizukuContextFlow.value.inputManager
                            if (m != null) {
                                m.key(KeyEvent.KEYCODE_APP_SWITCH)
                            } else {
                                toast("请先授权 Shizuku")
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
