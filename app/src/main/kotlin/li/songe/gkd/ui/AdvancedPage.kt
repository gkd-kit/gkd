package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.shizuku.shizukuCheckActivity
import li.songe.gkd.shizuku.shizukuCheckUserService
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.openUri
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AdvancedPage() {
    val context = LocalContext.current as MainActivity
    val vm = viewModel<AdvancedVm>()
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val snapshotCount by vm.snapshotCountFlow.collectAsState()

    var showEditPortDlg by remember {
        mutableStateOf(false)
    }
    if (showEditPortDlg) {
        var value by remember {
            mutableStateOf(store.httpServerPort.toString())
        }
        AlertDialog(title = { Text(text = "服务端口") }, text = {
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = "请输入 5000-65535 的整数")
                },
                onValueChange = {
                    value = it.filter { c -> c.isDigit() }.take(5)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = "${value.length} / 5",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showEditPortDlg = false
            }
        }, confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    val newPort = value.toIntOrNull()
                    if (newPort == null || !(5000 <= newPort && newPort <= 65535)) {
                        toast("请输入 5000-65535 的整数")
                        return@TextButton
                    }
                    storeFlow.value = store.copy(
                        httpServerPort = newPort
                    )
                    showEditPortDlg = false
                    if (HttpService.httpServerFlow.value != null) {
                        toast("已更新, 重启服务")
                    } else {
                        toast("已更新")
                    }
                }
            ) {
                Text(
                    text = "确认", modifier = Modifier
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showEditPortDlg = false }) {
                Text(
                    text = "取消"
                )
            }
        })
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            }, title = { Text(text = "高级设置") }, actions = {})
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Text(
                text = "Shizuku",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val shizukuOk by shizukuOkState.stateFlow.collectAsState()
            if (!shizukuOk) {
                AuthCard(title = "Shizuku授权",
                    desc = "高级模式:准确区别界面ID,强制模拟点击",
                    onAuthClick = {
                        try {
                            Shizuku.requestPermission(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            LogUtils.d("Shizuku授权错误", e.message)
                            context.mainVm.shizukuErrorFlow.value = true
                        }
                    })
                ShizukuFragment(false)
            } else {
                ShizukuFragment()
            }

            val server by HttpService.httpServerFlow.collectAsState()
            val httpServerRunning = server != null
            val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsState()

            Text(
                text = "HTTP服务",
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
                        if (!httpServerRunning) {
                            Text(
                                text = "在浏览器下连接调试工具",
                            )
                        } else {
                            Text(
                                text = "点击下面任意链接打开即可自动连接",
                            )
                            Row {
                                Text(
                                    text = "http://127.0.0.1:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier.clickable(onClick = throttle {
                                        openUri("http://127.0.0.1:${store.httpServerPort}")
                                    }),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(text = "仅本设备可访问")
                            }
                            localNetworkIps.forEach { host ->
                                Text(
                                    text = "http://${host}:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier.clickable(onClick = throttle {
                                        openUri("http://${host}:${store.httpServerPort}")
                                    })
                                )
                            }
                        }
                    }
                }
                Switch(
                    checked = httpServerRunning,
                    onCheckedChange = throttle(fn = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
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
                imageVector = Icons.Outlined.Edit,
                onClick = {
                    showEditPortDlg = true
                }
            )

            TextSwitch(
                title = "清除订阅",
                subtitle = "服务关闭时,删除内存订阅",
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
                subtitle = if (snapshotCount > 0) "存在 $snapshotCount 条记录" else "暂无记录",
                onClick = {
                    navController.toDestinationsNavigator().navigate(SnapshotPageDestination)
                }
            )


            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
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

            val floatingRunning by FloatingService.isRunning.collectAsState()
            TextSwitch(
                title = "悬浮窗服务",
                subtitle = "显示悬浮按钮点击保存快照",
                checked = floatingRunning,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        FloatingService.start()
                    } else {
                        FloatingService.stop()
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
                subtitle = "触发截屏时保存快照",
                suffix = "查看限制",
                onSuffixClick = {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = "限制说明",
                        text = "仅支持部分小米设备截屏触发\n\n只保存节点信息不保存图片, 用户需要在快照记录里替换截图",
                    )
                },
                checked = store.captureScreenshot
            ) {
                storeFlow.value = store.copy(
                    captureScreenshot = it
                )
            }

            TextSwitch(
                title = "隐藏状态栏",
                subtitle = "隐藏截图顶部状态栏",
                checked = store.hideSnapshotStatusBar
            ) {
                storeFlow.value = store.copy(
                    hideSnapshotStatusBar = it
                )
            }

            TextSwitch(
                title = "保存提示",
                subtitle = "保存时提示\"正在保存快照\"",
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
                onSuffixClick = {
                    openUri("https://gkd.li?r=1")
                },
                imageVector = Icons.Outlined.Edit,
                onClick = {
                    context.mainVm.showEditCookieDlgFlow.value = true
                }
            )

            Text(
                text = "界面记录",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = "记录界面",
                subtitle = "记录打开的应用及界面",
                checked = store.enableActivityLog
            ) {
                storeFlow.value = store.copy(
                    enableActivityLog = it
                )
            }
            SettingItem(
                title = "界面记录",
                onClick = {
                    navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                }
            )

            Text(
                text = "其它",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = "前台悬浮窗",
                subtitle = "添加透明悬浮窗",
                suffix = "查看作用",
                onSuffixClick = {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = "悬浮窗作用",
                        text = "1.提高 GKD 前台优先级, 降低被系统杀死概率\n2.提高点击响应速度, 关闭后可能导致点击缓慢或不点击",
                    )
                },
                checked = store.enableAbFloatWindow,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        enableAbFloatWindow = it
                    )
                })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

}

@Composable
private fun ShizukuFragment(enabled: Boolean = true) {
    val store by storeFlow.collectAsState()
    TextSwitch(
        title = "Shizuku-界面识别",
        subtitle = "更准确识别界面ID",
        checked = store.enableShizukuActivity,
        enabled = enabled,
        onCheckedChange = appScope.launchAsFn<Boolean>(Dispatchers.IO) {
            if (it) {
                toast("检测中")
                if (!shizukuCheckActivity()) {
                    toast("检测失败,无法使用")
                    return@launchAsFn
                }
                toast("已启用")
            }
            storeFlow.value = store.copy(
                enableShizukuActivity = it
            )
        })

    TextSwitch(
        title = "Shizuku-模拟点击",
        subtitle = "变更 clickCenter 为强制模拟点击",
        checked = store.enableShizukuClick,
        enabled = enabled,
        onCheckedChange = appScope.launchAsFn<Boolean>(Dispatchers.IO) {
            if (it) {
                toast("检测中")
                if (!shizukuCheckUserService()) {
                    toast("检测失败,无法使用")
                    return@launchAsFn
                }
                toast("已启用")
            }
            storeFlow.value = store.copy(
                enableShizukuClick = it
            )

        })

}
