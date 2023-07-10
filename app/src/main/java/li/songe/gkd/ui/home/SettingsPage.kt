package li.songe.gkd.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.delay
import li.songe.gkd.MainActivity
import li.songe.gkd.accessibility.GkdAbService
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.utils.Ext
import li.songe.gkd.utils.LocalLauncher
import li.songe.gkd.utils.LocalNavController
import li.songe.gkd.utils.Storage
import li.songe.gkd.utils.launchAsFn
import li.songe.gkd.utils.usePollState
import li.songe.gkd.utils.useTask
import rikka.shizuku.Shizuku
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.ui.destinations.AboutPageDestination
import li.songe.gkd.ui.destinations.SnapshotPageDestination

@Composable
fun SettingsPage() {
    val context = LocalContext.current as MainActivity
    val launcher = LocalLauncher.current
    val scope = rememberCoroutineScope()

    val navController = LocalNavController.current

    Column(
        modifier = Modifier
            .verticalScroll(
                state = rememberScrollState()
            )
            .padding(20.dp, 0.dp)
    ) {
        val gkdAccessRunning by usePollState { GkdAbService.isRunning() }
        TextSwitch("无障碍授权",
            "用于获取屏幕信息,点击屏幕上的控件",
            gkdAccessRunning,
            onCheckedChange = scope.launchAsFn<Boolean> {
                if (!it) return@launchAsFn
                ToastUtils.showShort("请先启动无障碍服务")
                delay(500)
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            })


        val shizukuIsOk by usePollState { shizukuIsSafeOK() }
        TextSwitch("Shizuku授权",
            "高级运行模式,能更准确识别界面活动ID",
            shizukuIsOk,
            onCheckedChange = scope.launchAsFn<Boolean> {
                if (!it) return@launchAsFn
                try {
                    Shizuku.requestPermission(Activity.RESULT_OK)
                } catch (e: Exception) {
                    ToastUtils.showShort("Shizuku可能没有运行")
                }
            })


        val canDrawOverlays by usePollState {
            Settings.canDrawOverlays(context)
        }
        Spacer(modifier = Modifier.height(5.dp))
        TextSwitch("悬浮窗授权",
            "用于后台提示,主动保存快照等功能",
            canDrawOverlays,
            onCheckedChange = scope.launchAsFn<Boolean> {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$context.packageName")
                    )
                    launcher.launch(intent) { resultCode, _ ->
                        if (resultCode != ComponentActivity.RESULT_OK) return@launch
                        if (!Settings.canDrawOverlays(context)) return@launch
                        val intent1 = Intent(context, FloatingService::class.java)
                        ContextCompat.startForegroundService(context, intent1)
                    }
                }
            })

        Spacer(modifier = Modifier.height(15.dp))

        val httpServerRunning by usePollState { HttpService.isRunning() }
        TextSwitch("HTTP服务",
            "开启HTTP服务, 以便在同一局域网下传递数据" + if (httpServerRunning) "\n${
                Ext.getIpAddressInLocalNetwork()
                    .map { host -> "http://${host}:${Storage.settings.httpServerPort}" }
                    .joinToString(",")
            }" else "\n暂无地址",
            httpServerRunning) {
            if (it) {
                HttpService.start()
            } else {
                HttpService.stop()
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        val screenshotRunning by usePollState { ScreenshotService.isRunning() }
        TextSwitch("截屏服务",
            "生成快照需要截取屏幕,Android>=11无需开启",
            screenshotRunning,
            scope.launchAsFn<Boolean> {
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

        Spacer(modifier = Modifier.height(5.dp))

        val floatingRunning by usePollState {
            FloatingService.isRunning()
        }
        TextSwitch("悬浮窗服务", "便于用户主动保存快照", floatingRunning) {
            if (it) {
                if (Settings.canDrawOverlays(context)) {
                    val intent = Intent(context, FloatingService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$context.packageName")
                    )
                    launcher.launch(intent) { resultCode, _ ->
                        if (resultCode != ComponentActivity.RESULT_OK) return@launch
                        if (!Settings.canDrawOverlays(context)) return@launch
                        val intent1 = Intent(context, FloatingService::class.java)
                        ContextCompat.startForegroundService(context, intent1)
                    }
                }
            } else {
                FloatingService.stop(context)
            }
        }


        Spacer(modifier = Modifier.height(15.dp))

        var enableService by remember { mutableStateOf(Storage.settings.enableService) }

        Spacer(modifier = Modifier.height(5.dp))
        TextSwitch(name = "服务开启",
            desc = "保持服务开启,根据订阅规则匹配屏幕目标节点",
            checked = enableService,
            onCheckedChange = {
                enableService = it
                Storage.settings.commit {
                    this.enableService = it
                }
            })

        Spacer(modifier = Modifier.height(5.dp))

        var excludeFromRecents by remember { mutableStateOf(Storage.settings.excludeFromRecents) }
        TextSwitch(name = "隐藏后台",
            desc = "在[最近任务]界面中隐藏本应用",
            checked = excludeFromRecents,
            onCheckedChange = {
                excludeFromRecents = it
                Storage.settings.commit {
                    this.excludeFromRecents = it
                }
            })

        Spacer(modifier = Modifier.height(5.dp))

        var enableConsoleLogOut by remember { mutableStateOf(Storage.settings.enableConsoleLogOut) }
        TextSwitch(name = "日志输出",
            desc = "保持日志输出到控制台",
            checked = enableConsoleLogOut,
            onCheckedChange = {
                enableConsoleLogOut = it
                LogUtils.getConfig().setConsoleSwitch(it)
                Storage.settings.commit {
                    this.enableConsoleLogOut = it
                }
            })

        Spacer(modifier = Modifier.height(5.dp))

        var notificationVisible by remember { mutableStateOf(Storage.settings.notificationVisible) }
        TextSwitch(name = "通知栏显示",
            desc = "通知栏显示可以降低系统杀后台的概率",
            checked = notificationVisible,
            onCheckedChange = {
                notificationVisible = it
                Storage.settings.commit {
                    this.notificationVisible = it
                }
            })
        Spacer(modifier = Modifier.height(5.dp))

        var enableScreenshot by remember {
            mutableStateOf(Storage.settings.enableCaptureSystemScreenshot)
        }
        Spacer(modifier = Modifier.height(5.dp))
        TextSwitch(
            "自动快照", "当用户截屏时,自动保存当前界面的快照,目前仅支持miui", enableScreenshot
        ) {
            enableScreenshot = it
            Storage.settings.commit {
                enableCaptureSystemScreenshot = it
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        Button(onClick = scope.useTask().launchAsFn {
            navController.navigate(SnapshotPageDestination)
        }) {
            Text(text = "查看快照记录")
        }

        Spacer(modifier = Modifier.height(5.dp))

        Button(onClick = scope.useTask().launchAsFn {
            navController.navigate(AboutPageDestination)
        }) {
            Text(text = "查看关于")
        }

    }
}