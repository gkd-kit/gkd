package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.*
import li.songe.gkd.MainActivity
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.service.GkdAccessService
import li.songe.gkd.service.HttpServerService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.component.TextSwitch


object DebugPage : Page<Unit, Unit> {
    override val path: String = "DebugPage"
    override val defaultParams = Unit
    override val content: @Composable BoxScope.(
        params: Unit,
        router: Router<Unit>
    ) -> Unit =
        { _, _ ->
            val context = LocalContext.current as MainActivity
            val composableScope = rememberCoroutineScope()
            Column(
                modifier = Modifier
                    .verticalScroll(
                        state = rememberScrollState()
                    )
                    .padding(20.dp)
            ) {
                StatusBar()

                Text("调试模式需要WIFI和另一台设备\n您可以一台设备开热点,另一台设备连入\n满足以上外部条件后, 本机需要开启以下服务")

                var httpServerRunning by remember { mutableStateOf(HttpServerService.isRunning()) }
                TextSwitch("HTTP服务(需WIFI)", httpServerRunning) {
                    httpServerRunning = it
                    if (it) {
                        HttpServerService.start()
                    } else {
                        HttpServerService.stop()
                    }
                }

                var screenshotRunning by remember { mutableStateOf(ScreenshotService.isRunning()) }
                TextSwitch("截屏服务", screenshotRunning) {
                    if (it) { /*indent*/ composableScope.launch {
                        if (!ScreenshotService.isRunning()) {
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val activityResult =
                                context.launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                            screenshotRunning =
                                if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                                    ScreenshotService.start(intent = activityResult.data!!)
                                    it
                                } else {
                                    !it
                                }
                        }
                    }
                    } else {
                        ScreenshotService.stop()
                        screenshotRunning = it
                    }
                }

                var gkdAccessRunning by remember { mutableStateOf(GkdAccessService.isRunning()) }
                LaunchedEffect(Unit) {
                    while (isActive) {
                        delay(500)
                        gkdAccessRunning = GkdAccessService.isRunning()
                    }
                }
                TextSwitch("无障碍服务", gkdAccessRunning) {
                    if (it) {/*indent*/composableScope.launch {
                        if (!GkdAccessService.isRunning()) {
                            ToastUtils.showShort("请先启动无障碍服务") // 可以用弹窗
                            delay(500)
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        }
                    }
                    } else {
                        ToastUtils.showLong("无障碍服务不可在调试模式中关闭")
                    }
                }
                var debugAvailable by remember { mutableStateOf(GkdAccessService.isRunning() && ScreenshotService.isRunning() && HttpServerService.isRunning()) }
                LaunchedEffect(Unit) {
                    while (isActive) {
                        delay(500)
                        debugAvailable = (GkdAccessService.isRunning() &&
                                ScreenshotService.isRunning() &&
                                HttpServerService.isRunning())
                    }
                }
                var serverUrl by remember { mutableStateOf<String?>(null) }
                var serverUrlQRCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(serverUrl) {
                    if (serverUrl != null) {
                        serverUrlQRCodeBitmap = withContext(Dispatchers.Default) {
                            context.barcodeEncoder.encodeBitmap(
                                serverUrl,
                                BarcodeFormat.QR_CODE,
                                400,
                                400
                            )
                        }
                    }
                }
//        LazyColumn(content = )
                LaunchedEffect(debugAvailable) {
                    serverUrl = if (debugAvailable) {
                        HttpServerService.getHttpUrl()
                    } else {
                        null
                    }
                }
                if (debugAvailable && serverUrl != null) {
                    Text("调试模式可用, 请使用同一局域网的另一台设备打开链接")
                    SelectionContainer {
                        Text("长按可复制: " + serverUrl!!)
                    }
                    if (serverUrlQRCodeBitmap != null) {
                        Text("也可扫描下方二维码")
                        Row {
                            Image(
                                serverUrlQRCodeBitmap!!.asImageBitmap(),
                                serverUrl,
                                Modifier
                                    .weight(1f)
                                    .heightIn(200.dp)
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
}