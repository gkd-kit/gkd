package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.permission.checkOrRequestPermission
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.shizuku.CommandResult
import li.songe.gkd.shizuku.newActivityTaskManager
import li.songe.gkd.shizuku.newUserService
import li.songe.gkd.shizuku.safeGetTasks
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.SnapshotPageDestination
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.openUri
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast
import rikka.shizuku.Shizuku

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AdvancedPage() {
    val context = LocalContext.current as MainActivity
    val scope = rememberCoroutineScope()
    val launcher = LocalLauncher.current
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val vm = hiltViewModel<AdvancedVm>()
    val snapshotCount by vm.snapshotCountFlow.collectAsState()

    var showPortDlg by remember {
        mutableStateOf(false)
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
            }, title = { Text(text = "高级模式") }, actions = {})
        }) { contentPadding ->
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
                    desc = "高级模式:准确识别界面ID,强制模拟点击",
                    onAuthClick = {
                        try {
                            Shizuku.requestPermission(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            LogUtils.d("Shizuku授权错误", e)
                            toast("Shizuku可能没有运行")
                        }
                    })
                ShizukuFragment(false)
            } else {
                ShizukuFragment()
            }

            val httpServerRunning by HttpService.isRunning.collectAsState()
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
                                    modifier = Modifier.clickable {
                                        context.openUri("http://127.0.0.1:${store.httpServerPort}")
                                    }
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(text = "仅本设备可访问")
                            }
                            localNetworkIps.forEach { host ->
                                Text(
                                    text = "http://${host}:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        context.openUri("http://${host}:${store.httpServerPort}")
                                    }
                                )
                            }
                        }
                    }
                }
                Switch(
                    checked = httpServerRunning,
                    onCheckedChange = scope.launchAsFn<Boolean> {
                        if (it) {
                            if (!checkOrRequestPermission(context, notificationState)) {
                                return@launchAsFn
                            }
                            HttpService.start()
                        } else {
                            HttpService.stop()
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .clickable { showPortDlg = true }
                    .itemPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "服务端口",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = store.httpServerPort.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
            }

            TextSwitch(
                name = "保留内存订阅",
                desc = "当HTTP服务关闭时,保留内存订阅",
                checked = !store.autoClearMemorySubs
            ) {
                storeFlow.value = store.copy(
                    autoClearMemorySubs = !it
                )
            }

            Text(
                text = "快照",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(
                title = "快照记录" + (if (snapshotCount > 0) "-$snapshotCount" else ""),
                onClick = {
                    navController.navigate(SnapshotPageDestination)
                }
            )

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val screenshotRunning by ScreenshotService.isRunning.collectAsState()
                TextSwitch(
                    name = "截屏服务",
                    desc = "生成快照需要获取屏幕截图",
                    checked = screenshotRunning,
                    onCheckedChange = scope.launchAsFn<Boolean> {
                        if (it) {
                            if (!checkOrRequestPermission(context, notificationState)) {
                                return@launchAsFn
                            }
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val activityResult =
                                launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
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
                name = "悬浮窗服务",
                desc = "显示截屏按钮,便于用户主动保存快照",
                checked = floatingRunning,
                onCheckedChange = scope.launchAsFn<Boolean> {
                    if (it) {
                        if (!checkOrRequestPermission(context, notificationState)) {
                            return@launchAsFn
                        }
                        if (!checkOrRequestPermission(context, canDrawOverlaysState)) {
                            return@launchAsFn
                        }
                        val intent = Intent(context, FloatingService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        FloatingService.stop(context)
                    }
                }
            )

            TextSwitch(
                name = "音量快照",
                desc = "音量变化时生成快照,悬浮窗按钮不工作时使用",
                checked = store.captureVolumeChange
            ) {
                storeFlow.value = store.copy(
                    captureVolumeChange = it
                )
            }

            TextSwitch(
                name = "截屏快照",
                desc = "当用户截屏时保存快照(需手动替换快照图片),仅支持部分小米设备",
                checked = store.captureScreenshot
            ) {
                storeFlow.value = store.copy(
                    captureScreenshot = it
                )
            }

            TextSwitch(
                name = "隐藏快照状态栏",
                desc = "当保存快照时,隐藏截图里的顶部状态栏高度区域",
                checked = store.hideSnapshotStatusBar
            ) {
                storeFlow.value = store.copy(
                    hideSnapshotStatusBar = it
                )
            }

            Text(
                text = "其它",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(name = "前台悬浮窗",
                desc = "添加透明悬浮窗,关闭可能导致不点击/点击缓慢",
                checked = store.enableAbFloatWindow,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        enableAbFloatWindow = it
                    )
                })

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showPortDlg) {
        Dialog(onDismissRequest = { showPortDlg = false }) {
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
            }, onDismissRequest = { showPortDlg = false }, confirmButton = {
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
                        showPortDlg = false
                    }
                ) {
                    Text(
                        text = "确认", modifier = Modifier
                    )
                }
            }, dismissButton = {
                TextButton(onClick = { showPortDlg = false }) {
                    Text(
                        text = "取消"
                    )
                }
            })
        }
    }
}

@Composable
private fun ShizukuFragment(enabled: Boolean = true) {
    val store by storeFlow.collectAsState()
    TextSwitch(name = "Shizuku-界面识别",
        desc = "更准确识别界面ID",
        checked = store.enableShizukuActivity,
        enabled = enabled,
        onCheckedChange = { enableShizuku ->
            if (enableShizuku) {
                appScope.launchTry(Dispatchers.IO) {
                    // 校验方法是否适配, 再允许使用 shizuku
                    val tasks =
                        newActivityTaskManager()?.safeGetTasks()?.firstOrNull()
                    if (tasks != null) {
                        storeFlow.value = store.copy(
                            enableShizukuActivity = true
                        )
                    } else {
                        toast("Shizuku-界面识别校验失败,无法使用")
                    }
                }
            } else {
                storeFlow.value = store.copy(
                    enableShizukuActivity = false
                )
            }
        })

    TextSwitch(
        name = "Shizuku-模拟点击",
        desc = "变更 clickCenter 为强制模拟点击",
        checked = store.enableShizukuClick,
        enabled = enabled,
        onCheckedChange = { enableShizuku ->
            if (enableShizuku) {
                appScope.launchTry(Dispatchers.IO) {
                    val service = newUserService()
                    val result = service.userService.execCommand("input tap 0 0")
                    service.destroy()
                    if (json.decodeFromString<CommandResult>(result).code == 0) {
                        storeFlow.update { it.copy(enableShizukuClick = true) }
                    } else {
                        toast("Shizuku-模拟点击校验失败,无法使用")
                    }
                }
            } else {
                storeFlow.value = store.copy(
                    enableShizukuClick = false
                )
            }

        })

}