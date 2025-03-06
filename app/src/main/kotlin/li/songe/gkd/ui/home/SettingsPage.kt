package li.songe.gkd.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.AboutPageDestination
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextMenu
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.ui.theme.supportDynamicColor
import li.songe.gkd.util.DarkThemeOption
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.findOption
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle

val settingsNav = BottomNavItem(
    label = "设置", icon = Icons.Outlined.Settings
)

@Composable
fun useSettingsPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val vm = viewModel<HomeVm>()

    var showToastInputDlg by remember {
        mutableStateOf(false)
    }
    var showNotifTextInputDlg by remember {
        mutableStateOf(false)
    }


    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.clickToast)
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
                    modifier = Modifier.autoFocus()
                )
            },
            onDismissRequest = { showToastInputDlg = false },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    storeFlow.update { it.copy(clickToast = value) }
                    showToastInputDlg = false
                }) {
                    Text(
                        text = "确认",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showToastInputDlg = false }) {
                    Text(
                        text = "取消",
                    )
                }
            }
        )
    }
    if (showNotifTextInputDlg) {
        var value by remember {
            mutableStateOf(store.customNotifText)
        }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "通知文案")
                    IconButton(onClick = throttle {
                        mainVm.dialogFlow.updateDialogOptions(
                            title = "文案规则",
                            text = "通知文案支持变量替换,规则如下\n\${i} 全局规则数\n\${k} 应用数\n\${u} 应用规则组数\n\${n} 触发次数\n\n示例模板\n\${i}全局/\${k}应用/\${u}规则组/\${n}触发\n\n替换结果\n0全局/1应用/2规则组/3触发",
                        )
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = null,
                        )
                    }
                }
            },
            text = {
                val maxCharLen = 64
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "请输入文案内容,支持变量替换")
                    },
                    onValueChange = {
                        value = if (it.length > maxCharLen) it.take(maxCharLen) else it
                    },
                    maxLines = 4,
                    supportingText = {
                        Text(
                            text = "${value.length} / $maxCharLen",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier.autoFocus()
                )
            },
            onDismissRequest = {
                showNotifTextInputDlg = false
            },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    storeFlow.update { it.copy(customNotifText = value) }
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    return ScaffoldExt(
        navItem = settingsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = settingsNav.label,
                )
            })
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = "常规",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = "触发提示",
                subtitle = store.clickToast,
                checked = store.toastWhenClick,
                modifier = Modifier.clickable {
                    showToastInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            AnimatedVisibility(visible = store.toastWhenClick) {
                TextSwitch(
                    title = "系统提示",
                    subtitle = "系统样式触发提示",
                    suffix = "查看限制",
                    onSuffixClick = {
                        mainVm.dialogFlow.updateDialogOptions(
                            title = "限制说明",
                            text = "系统 Toast 存在频率限制, 触发过于频繁会被系统强制不显示\n\n如果只使用开屏一类低频率规则可使用系统提示, 否则建议关闭此项使用自定义样式提示",
                        )
                    },
                    checked = store.useSystemToast,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            useSystemToast = it
                        )
                    })
            }

            val subsStatus by vm.subsStatusFlow.collectAsState()
            TextSwitch(
                title = "通知文案",
                subtitle = if (store.useCustomNotifText) store.customNotifText else subsStatus,
                checked = store.useCustomNotifText,
                modifier = Modifier.clickable {
                    showNotifTextInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        useCustomNotifText = it
                    )
                })

            TextSwitch(
                title = "后台隐藏",
                subtitle = "在[最近任务]中隐藏本应用",
                checked = store.excludeFromRecents,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        excludeFromRecents = it
                    )
                })

            Text(
                text = "主题",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextMenu(
                title = "深色模式",
                option = DarkThemeOption.allSubObject.findOption(store.enableDarkTheme)
            ) {
                storeFlow.update { s -> s.copy(enableDarkTheme = it.value) }
            }

            if (supportDynamicColor) {
                TextSwitch(title = "动态配色",
                    subtitle = "配色跟随系统主题",
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            enableDynamicColor = it
                        )
                    })
            }

            Text(
                text = "其它",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = "高级设置", onClick = {
                navController.toDestinationsNavigator().navigate(AdvancedPageDestination)
            })

            SettingItem(title = "关于", onClick = {
                navController.toDestinationsNavigator().navigate(AboutPageDestination)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
