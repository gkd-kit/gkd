package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.accessibility.GkdAbService
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.debug.server.HttpService
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.util.Ext
import li.songe.gkd.util.Ext.LocalLauncher
import li.songe.gkd.util.Ext.usePollState
import li.songe.gkd.util.Storage
import li.songe.router.Page


val DebugPage = Page {
    val context = LocalContext.current as ComponentActivity
    val launcher = LocalLauncher.current
    val scope = rememberCoroutineScope()

    val httpServerRunning by usePollState { HttpService.isRunning() }
    val screenshotRunning by usePollState { ScreenshotService.isRunning() }
    val gkdAccessRunning by usePollState { GkdAbService.isRunning() }
    val floatingRunning by usePollState {
        FloatingService.isRunning() && Settings.canDrawOverlays(
            context
        )
    }


    val debugAvailable by remember {
        derivedStateOf { httpServerRunning }
    }

    val serverUrl by remember {
        derivedStateOf {
            if (debugAvailable) {
                Ext.getIpAddressInLocalNetwork()
                    .map { host -> "http://${host}:${Storage.settings.httpServerPort}" }
                    .joinToString("\n")
            } else {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(
                state = rememberScrollState()
            )
            .padding(20.dp)
    ) {
        StatusBar()

        Text("调试模式需要WIFI和另一台设备\n您可以一台设备开热点,另一台设备连入\n满足以上外部条件后, 本机需要开启以下服务")

        TextSwitch("HTTP服务(需WIFI)", httpServerRunning) {
            if (it) {
                HttpService.start()
            } else {
                HttpService.stop()
            }
        }

        TextSwitch("截屏服务", screenshotRunning) {
            if (it) {
                scope.launch {
                    val mediaProjectionManager =
                        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val activityResult =
                        launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                    if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                        ScreenshotService.start(intent = activityResult.data!!)
                    }
                }
            } else {
                ScreenshotService.stop()
            }
        }

        TextSwitch("无障碍服务", gkdAccessRunning) {
            if (it) {
                scope.launch {
                    ToastUtils.showShort("请先启动无障碍服务")
                    delay(500)
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            } else {
                ToastUtils.showLong("无障碍服务不可在调试模式中关闭")
            }
        }

        TextSwitch("悬浮窗服务", floatingRunning) {
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

        if (debugAvailable && serverUrl != null) {
            Text("调试模式可用, 请使用同一局域网的另一台设备打开链接")
            SelectionContainer {
                Text("长按可复制: " + serverUrl!!)
            }
        }
    }

}