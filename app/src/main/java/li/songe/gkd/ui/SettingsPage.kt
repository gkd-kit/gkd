package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import li.songe.gkd.MainActivity
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.util.Ext
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateStore
import li.songe.gkd.util.usePollState
import rikka.shizuku.Shizuku

val settingsNav = BottomNavItem(
    label = "设置", icon = SafeR.ic_cog, route = "settings"
)

@Composable
fun SettingsPage() {
    val context = LocalContext.current as MainActivity
    val launcher = LocalLauncher.current
    val scope = rememberCoroutineScope()

    val store by storeFlow.collectAsState()


    var showPortDlg by remember {
        mutableStateOf(false)
    }

    Scaffold(topBar = {
        TopAppBar(backgroundColor = Color(0xfff8f9f9), title = {
            Text(
                text = "设置", color = Color.Black
            )
        })
    }, content = { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(
                    state = rememberScrollState()
                )
                .padding(0.dp, 10.dp)
                .padding(contentPadding)
        ) {

            val shizukuIsOk by usePollState { shizukuIsSafeOK() }
            TextSwitch(name = "Shizuku授权",
                desc = "高级运行模式,能更准确识别界面活动ID",
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
            TextSwitch(name = "悬浮窗授权",
                desc = "用于后台提示,主动保存快照等功能",
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

            val httpServerRunning by usePollState { HttpService.isRunning() }
            TextSwitch(
                name = "HTTP服务",
                desc = "开启HTTP服务, 以便在同一局域网下传递数据" + if (httpServerRunning) "\n${
                    Ext.getIpAddressInLocalNetwork()
                        .map { host -> "http://${host}:${store.httpServerPort}" }.joinToString(",")
                }" else "\n暂无地址",
                httpServerRunning
            ) {
                if (it) {
                    HttpService.start()
                } else {
                    HttpService.stop()
                }
            }

            SettingItem(title = "HTTP服务端口-${store.httpServerPort}") {
                showPortDlg = true
            }

            val screenshotRunning by usePollState { ScreenshotService.isRunning() }
            TextSwitch(name = "截屏服务",
                desc = "生成快照需要截取屏幕,Android>=11无需开启",
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

            val floatingRunning by usePollState {
                FloatingService.isRunning()
            }
            TextSwitch(name = "悬浮窗服务", desc = "便于用户主动保存快照", floatingRunning) {
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


            TextSwitch(name = "隐藏后台",
                desc = "在[最近任务]界面中隐藏本应用",
                checked = store.excludeFromRecents,
                onCheckedChange = {
                    updateStore(
                        store.copy(
                            excludeFromRecents = it
                        )
                    )
                })

            TextSwitch(name = "日志输出",
                desc = "保持日志输出到控制台",
                checked = store.enableConsoleLogOut,
                onCheckedChange = {
                    updateStore(
                        store.copy(
                            enableConsoleLogOut = it
                        )
                    )
                })


            Spacer(modifier = Modifier.height(5.dp))
            TextSwitch(
                "自动快照",
                "当用户截屏时,自动保存当前界面的快照,目前仅支持miui",
                store.enableCaptureScreenshot
            ) {
                updateStore(
                    store.copy(
                        enableCaptureScreenshot = it
                    )
                )
            }
        }
    })

    if (showPortDlg) {
        Dialog(onDismissRequest = { showPortDlg = false }) {
            var value by remember {
                mutableStateOf(store.httpServerPort.toString())
            }
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .width(300.dp)
            ) {
                TextField(value = value, onValueChange = { value = it.trim() }, singleLine = true)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "取消",
                        modifier = Modifier
                            .clickable { showPortDlg = false }
                            .padding(5.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(text = "确认", modifier = Modifier
                        .clickable {
                            val newPort = value.toIntOrNull()
                            if (newPort == null || !(5000 <= newPort && newPort <= 65535)) {
                                ToastUtils.showShort("请输入在 5000~65535 的任意数字")
                                return@clickable
                            }
                            updateStore(
                                store.copy(
                                    httpServerPort = newPort
                                )
                            )
                            showPortDlg = false
                        }
                        .padding(5.dp))
                }
            }
        }
    }

}