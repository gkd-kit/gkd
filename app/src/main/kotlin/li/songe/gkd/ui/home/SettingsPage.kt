package li.songe.gkd.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.AboutPageDestination
import li.songe.gkd.ui.destinations.DebugPageDestination
import li.songe.gkd.util.LoadStatus
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.buildLogFile
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.checkUpdatingFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast
import kotlin.math.sin

val settingsNav = BottomNavItem(
    label = "设置", icon = Icons.Outlined.Settings
)

@Composable
fun useSettingsPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val vm = hiltViewModel<HomeVm>()
    val uploadStatus by vm.uploadStatusFlow.collectAsState()

    var showSubsIntervalDlg by remember {
        mutableStateOf(false)
    }
    var showEnableDarkThemeDlg by remember {
        mutableStateOf(false)
    }
    var showToastInputDlg by remember {
        mutableStateOf(false)
    }

    var showShareLogDlg by remember {
        mutableStateOf(false)
    }

    val checkUpdating by checkUpdatingFlow.collectAsState()

    if (showSubsIntervalDlg) {
        Dialog(onDismissRequest = { showSubsIntervalDlg = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                UpdateTimeOption.allSubObject.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (option.value == store.updateSubsInterval),
                                onClick = {
                                    storeFlow.update { it.copy(updateSubsInterval = option.value) }
                                })
                            .padding(horizontal = 16.dp)
                    ) {
                        RadioButton(
                            selected = (option.value == store.updateSubsInterval),
                            onClick = {
                                storeFlow.update { it.copy(updateSubsInterval = option.value) }
                            })
                        Text(
                            text = option.label, modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showEnableDarkThemeDlg) {
        Dialog(onDismissRequest = { showEnableDarkThemeDlg = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                darkThemeRadioOptions.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = (option.second == store.enableDarkTheme),
                                onClick = {
                                    storeFlow.value =
                                        store.copy(enableDarkTheme = option.second)
                                })
                            .padding(horizontal = 16.dp)
                    ) {
                        RadioButton(
                            selected = (option.second == store.enableDarkTheme),
                            onClick = {
                                storeFlow.value = store.copy(enableDarkTheme = option.second)
                            })
                        Text(
                            text = option.first, modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.clickToast)
        }
        val maxCharLen = 32
        AlertDialog(title = { Text(text = "请输入提示文字") }, text = {
            OutlinedTextField(
                value = value,
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
            )
        }, onDismissRequest = { showToastInputDlg = false }, confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    storeFlow.value = store.copy(
                        clickToast = value
                    )
                    showToastInputDlg = false
                }
            ) {
                Text(
                    text = "确认",
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showToastInputDlg = false }) {
                Text(
                    text = "取消",
                )
            }
        })
    }

    if (showShareLogDlg) {
        Dialog(onDismissRequest = { showShareLogDlg = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = "调用系统分享", modifier = Modifier
                        .clickable(onClick = {
                            showShareLogDlg = false
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.shareFile(logZipFile, "分享日志文件")
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = "生成链接(需科学上网)", modifier = Modifier
                        .clickable(onClick = {
                            showShareLogDlg = false
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                vm.uploadZip(logZipFile)
                            }
                        })
                        .then(modifier)
                )
            }
        }
    }

    when (val uploadStatusVal = uploadStatus) {
        is LoadStatus.Failure -> {
            AlertDialog(
                title = { Text(text = "上传失败") },
                text = {
                    Text(text = uploadStatusVal.exception.let {
                        it.message ?: it.toString()
                    })
                },
                onDismissRequest = { vm.uploadStatusFlow.value = null },
                confirmButton = {
                    TextButton(onClick = {
                        vm.uploadStatusFlow.value = null
                    }) {
                        Text(text = "关闭")
                    }
                },
            )
        }

        is LoadStatus.Loading -> {
            AlertDialog(
                title = { Text(text = "上传文件中") },
                text = {
                    LinearProgressIndicator(
                        progress = { uploadStatusVal.progress },
                    )
                },
                onDismissRequest = { },
                confirmButton = {
                    TextButton(onClick = {
                        vm.uploadJob?.cancel(CancellationException("终止上传"))
                        vm.uploadJob = null
                    }) {
                        Text(text = "终止上传")
                    }
                },
            )
        }

        is LoadStatus.Success -> {
            AlertDialog(title = { Text(text = "上传完成") }, text = {
                Text(text = uploadStatusVal.result.href)
            }, onDismissRequest = {}, dismissButton = {
                TextButton(onClick = {
                    vm.uploadStatusFlow.value = null
                }) {
                    Text(text = "关闭")
                }
            }, confirmButton = {
                TextButton(onClick = {
                    ClipboardUtils.copyText(uploadStatusVal.result.href)
                    toast("复制成功")
                    vm.uploadStatusFlow.value = null
                }) {
                    Text(text = "复制")
                }
            })
        }

        else -> {}
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    return ScaffoldExt(
        navItem = settingsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = settingsNav.label,
                    )
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(padding)
        ) {

            Text(
                text = "常规",
                modifier = Modifier.padding(16.dp, 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(name = "触发提示",
                desc = store.clickToast,
                checked = store.toastWhenClick,
                modifier = Modifier.clickable {
                    showToastInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            TextSwitch(
                name = "后台隐藏",
                desc = "在[最近任务]界面中隐藏本应用",
                checked = store.excludeFromRecents,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        excludeFromRecents = it
                    )
                })

            Row(
                modifier = Modifier
                    .clickable {
                        showEnableDarkThemeDlg = true
                    }
                    .padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "深色模式",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = darkThemeRadioOptions.find { it.second == store.enableDarkTheme }?.first
                            ?: store.enableDarkTheme.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "more"
                    )
                }
            }

            Text(
                text = "更新",
                modifier = Modifier.padding(16.dp, 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier
                    .clickable {
                        showSubsIntervalDlg = true
                    }
                    .padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "自动更新订阅",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = UpdateTimeOption.allSubObject.find { it.value == store.updateSubsInterval }?.label
                            ?: store.updateSubsInterval.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "more"
                    )
                }
            }

            TextSwitch(name = "自动更新应用",
                desc = "打开应用时自动检测是否存在新版本",
                checked = store.autoCheckAppUpdate,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        autoCheckAppUpdate = it
                    )
                })

            Row(
                modifier = Modifier
                    .clickable(
                        onClick = appScope.launchAsFn {
                            if (checkUpdatingFlow.value) return@launchAsFn
                            val newVersion = checkUpdate()
                            if (newVersion == null) {
                                toast("暂无更新")
                            }
                        }
                    )
                    .fillMaxWidth()
                    .padding(16.dp, 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "检查更新",
                    style = MaterialTheme.typography.bodyLarge,
                )
                RotatingLoadingIcon(loading = checkUpdating)
            }

            Text(
                text = "日志",
                modifier = Modifier.padding(16.dp, 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(name = "保存日志",
                desc = "保存最近7天日志,关闭后无法定位解决错误",
                checked = store.log2FileSwitch,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        log2FileSwitch = it
                    )
                    if (!it) {
                        appScope.launchTry(Dispatchers.IO) {
                            val logFiles = LogUtils.getLogFiles()
                            if (logFiles.isNotEmpty()) {
                                logFiles.forEach { f ->
                                    f.delete()
                                }
                                toast("已删除全部日志")
                            }
                        }
                    }
                }
            )

            SettingItem(title = "分享日志", onClick = {
                showShareLogDlg = true
            })

            Text(
                text = "其它",
                modifier = Modifier.padding(16.dp, 12.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = "高级模式", onClick = {
                navController.navigate(DebugPageDestination)
            })

            SettingItem(title = "关于", onClick = {
                navController.navigate(AboutPageDestination)
            })

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

sealed class UpdateTimeOption(val value: Long, val label: String) {
    data object Pause : UpdateTimeOption(-1, "暂停")
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, "每天")
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3, "每3天")
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7, "每7天")

    companion object {
        val allSubObject by lazy { arrayOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

private val darkThemeRadioOptions = arrayOf(
    "跟随系统" to null,
    "启用" to true,
    "关闭" to false,
)

@Composable
fun RotatingLoadingIcon(loading: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(loading) {
        if (loading) {
            rotation.animateTo(
                targetValue = rotation.value + 180f,
                animationSpec = tween(
                    durationMillis = 250,
                    easing = { x -> sin(Math.PI / 2 * (x - 1f)).toFloat() + 1f }
                )
            )
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else if (rotation.value != 0f) {
            rotation.animateTo(
                targetValue = rotation.value + 180f,
                animationSpec = tween(
                    durationMillis = 250,
                    easing = { x -> sin(Math.PI / 2 * x).toFloat() }
                )
            )
        }
    }
    Icon(
        imageVector = Icons.Default.Autorenew,
        contentDescription = null,
        modifier = Modifier.graphicsLayer(rotationZ = rotation.value)
    )
}

