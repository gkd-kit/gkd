package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.shizuku.newActivityTaskManager
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.destinations.SnapshotPageDestination
import li.songe.gkd.util.Ext
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStorage
import li.songe.gkd.util.usePollState
import rikka.shizuku.Shizuku

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun DebugPage() {
    val context = LocalContext.current as MainActivity
    val launcher = LocalLauncher.current
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()

    var showPortDlg by remember {
        mutableStateOf(false)
    }

    Scaffold(topBar = {
        SimpleTopAppBar(
            onClickIcon = { navController.popBackStack() }, title = "高级模式"
        )
    }, content = { contentPadding ->
        Column(
            modifier = Modifier
                .padding(0.dp, 10.dp)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val shizukuIsOk by usePollState { shizukuIsSafeOK() }
            LaunchedEffect(key1 = shizukuIsOk, block = {
                if (shizukuIsOk) {
//                    check method type
                    appScope.launchTry(Dispatchers.IO) {
                        newActivityTaskManager()
                    }
                }
            })
            if (!shizukuIsOk) {
                AuthCard(title = "Shizuku授权",
                    desc = "高级运行模式,能更准确识别界面活动ID\nAndroid14暂无法使用",
                    onAuthClick = {
                        try {
                            Shizuku.requestPermission(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            ToastUtils.showShort("Shizuku可能没有运行")
                        }
                    })
                Divider()
            }

            val httpServerRunning by usePollState { HttpService.isRunning() }
            TextSwitch(
                name = "HTTP服务",
                desc = "开启HTTP服务, 以便在同一局域网下传递数据" + if (httpServerRunning) "\n${
                    Ext.getIpAddressInLocalNetwork()
                        .map { host -> "http://${host}:${store.httpServerPort}" }.joinToString(",")
                }" else "",
                checked = httpServerRunning
            ) {
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    ToastUtils.showShort("需要通知权限")
                    return@TextSwitch
                }
                if (it) {
                    HttpService.start()
                } else {
                    HttpService.stop()
                }
            }
            Divider()

            SettingItem(
                title = "HTTP服务端口-${store.httpServerPort}", imageVector = Icons.Default.Edit
            ) {
                showPortDlg = true
            }
            Divider()

            TextSwitch(
                name = "自动清除内存订阅",
                desc = "当HTTP服务关闭时,清除内存订阅",
                checked = store.autoClearMemorySubs
            ) {
                updateStorage(
                    storeFlow, store.copy(
                        autoClearMemorySubs = it
                    )
                )
            }
            Divider()

            // android 11 以上可以使用无障碍服务获取屏幕截图
            // Build.VERSION.SDK_INT < Build.VERSION_CODES.R
            val screenshotRunning by usePollState { ScreenshotService.isRunning() }
            TextSwitch(name = "截屏服务",
                desc = "生成快照需要获取屏幕截图,Android11无需开启",
                checked = screenshotRunning,
                onCheckedChange = appScope.launchAsFn<Boolean> {
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        ToastUtils.showShort("需要通知权限")
                        return@launchAsFn
                    }
                    if (it) {
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
                })
            Divider()


            val floatingRunning by usePollState {
                FloatingService.isRunning()
            }
            TextSwitch(
                name = "悬浮窗服务",
                desc = "显示截屏按钮,便于用户主动保存快照",
                checked = floatingRunning
            ) {
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    ToastUtils.showShort("需要通知权限")
                    return@TextSwitch
                }

                if (it) {
                    if (Settings.canDrawOverlays(context)) {
                        val intent = Intent(context, FloatingService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        ToastUtils.showShort("需要悬浮窗权限")
                    }
                } else {
                    FloatingService.stop(context)
                }
            }
            Divider()

            SettingItem(title = "快照记录", onClick = {
                navController.navigate(SnapshotPageDestination)
            })
        }
    })

    if (showPortDlg) {
        Dialog(onDismissRequest = { showPortDlg = false }) {
            var value by remember {
                mutableStateOf(store.httpServerPort.toString())
            }
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Text(text = "请输入新端口", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(
                    horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { showPortDlg = false }) {
                        Text(
                            text = "取消", modifier = Modifier
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    TextButton(onClick = {
                        val newPort = value.toIntOrNull()
                        if (newPort == null || !(5000 <= newPort && newPort <= 65535)) {
                            ToastUtils.showShort("请输入在 5000~65535 的任意数字")
                            return@TextButton
                        }
                        updateStorage(
                            storeFlow, store.copy(
                                httpServerPort = newPort
                            )
                        )
                        showPortDlg = false
                    }) {
                        Text(
                            text = "确认", modifier = Modifier
                        )
                    }
                }
            }
        }
    }
}