package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.data.GkdRule
import li.songe.gkd.data.GkdSubscription
import li.songe.gkd.data.NodeData
import li.songe.gkd.util.GkdDebugServer.getIpAddressInLocalNetwork
import java.io.FileOutputStream
import java.io.IOException


/**
 * demo: https://juejin.cn/post/6844903589127651335
 */
@SuppressLint("WrongConstant")
class GkdService : AccessibilityService() {
    private val scope by lazy { CoroutineScope(Dispatchers.Default + Job()) }

    override fun onCreate() {
        super.onCreate()
        LogUtils.d("onCreate")
//        NotificationCompat.Builder(this, "")
        mediaProjectionManager =
            getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var captured = false


    private var screenshotIntent: Intent? = null

    //    private var screenshotIntent: Intent? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server == null &&
            screenshotIntent == null &&
            intent?.getBooleanExtra(
                "screenshot",
                false
            ) == true
        ) {

            createNotificationChannel()
            screenshotIntent = intent
            mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK, intent)
            scope.launch(Dispatchers.IO) {
                val localIpAddress = getIpAddressInLocalNetwork()
                if (localIpAddress != null) {
                    LogUtils.d("http://$localIpAddress:$port/")
                }
                server = getServer()
                server!!.start(true)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotificationChannel() {
        val channelId = "CHANNEL_ID"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_app_2)
            .setContentTitle("textTitle")
            .setContentText("textContent")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)

        val name = "channel_name"
        val descriptionText = "channel_description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                110,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(110, notification)
        }
    }


    private var job: Job? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        LogUtils.d("onServiceConnected")
        job = scope.launch {
            while (true) {
                if (!this.isActive) {
                    break
                }

                val window: AccessibilityNodeInfo = rootInActiveWindow ?: continue
                val targetPackageName = window.packageName?.toString() ?: continue

                try {
                    gkdRule.traverse(targetPackageName, targetClassName) {
                        val targetNodeInfo = it.collect(window) ?: return@traverse Unit
                        LogUtils.d(it.toString(), targetNodeInfo)
                        it.click(targetNodeInfo, this@GkdService)
                        return@traverse true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(250)
            }
        }
    }


    private val gkdRule by lazy {
        GkdRule().apply {
            val source = assets.open("ad-internal.gkd.json").readBytes().toString(Charsets.UTF_8)
            load(GkdSubscription.parse(source))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.className == null) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className.toString()
//                val packageName = event.packageName.toString()
//                在桌面和应用之间来回切换, 大概率导致识别失败
                if (!className.startsWith("android.") &&
                    !className.startsWith("androidx.") &&
                    !className.startsWith("com.android.")
                ) {
//                    className.startsWith(packageName)
                    val rootPackageName = rootInActiveWindow?.packageName?.toString() ?: ""
                    if ((className == "com.miui.home.launcher.Launcher" && rootPackageName != "com.miui.home")) {
//                        小米手机 上滑手势, 导致 活动名 不属于包名
                    } else {
                        targetClassName = className
                    }
                }
            }
            else -> {
            }
        }
    }

    private var targetClassName = ""
        set(value) {
            if (field != value) {
                field = value
                var count = 0
                gkdRule.traverse(
                    (rootInActiveWindow?.packageName?.toString() ?: ""),
                    targetClassName
                ) {
                    count++
                }
                LogUtils.dTag(
                    "updateClassName",
                    field,
                    rootInActiveWindow?.packageName,
                    count
                )

            }
        }

    override fun onInterrupt() {
        job?.cancel()
        LogUtils.d("onInterrupt")

    }

    override fun onDestroy() {
        super.onDestroy()
        tempBitmap?.recycle()
        job?.cancel()
        scope.launch(Dispatchers.IO) {
            server?.stop(1000, 2000)
            scope.cancel()
        }
        LogUtils.d("onDestroy")
    }

    private var server: NettyApplicationEngine? = null

    private val port = 8888
    private fun getServer(): NettyApplicationEngine {
        return embeddedServer(Netty, port) {
            routing {
                get("/") {
                    call.respondText("<html>hello world</html>", ContentType.Text.Html)
                }
                get("/api/screenshot") {
                    withContext(Dispatchers.IO) {
                        try {
//                            val bitmap = screenshot2()
//                            if (bitmap != null) {
//                                call.respondOutputStream(ContentType.Image.Any) {
//                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
//                                    bitmap.recycle()
//                                }
//                            } else {
//                            }
                            screenshot()
                            var i = 0
                            while (true) {
                                delay(50)
                                i += 50
                                if (tempBitmap != null) {
                                    call.respondOutputStream(ContentType.Image.Any) {
                                        tempBitmap!!.compress(Bitmap.CompressFormat.PNG, 100, this)
                                        tempBitmap!!.recycle()
                                        tempBitmap = null
                                    }
                                    break
                                } else if (i > 10_000) {
                                    call.respondText(
                                        ContentType.Application.Json,
                                        HttpStatusCode.NotFound
                                    ) { """{"message":"time out"}""" }
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            call.respondText(
                                ContentType.Application.Json
                            ) { """{"message":"error"}""" }
                        }
                    }
                }
                get("/api/node") {
                    try {
                        return@get call.respondText(
                            NodeData.stringify(rootInActiveWindow),
                            ContentType.Application.Json
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }


    @SuppressLint("WrongConstant")
    private fun screenshot() {
        imageReader?.setOnImageAvailableListener(onImageAvailableListener, handler)
        mediaProjection?.registerCallback(MediaProjectionStopCallback(), handler)
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        var image: Image? = null
        val fos: FileOutputStream? = null
        var bitmapWithStride: Bitmap? = null
        val bitmap: Bitmap?

        try {
            image = reader.acquireLatestImage()
            if (image != null && !captured) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride

                bitmapWithStride = Bitmap.createBitmap(
                    rowStride / pixelStride,
                    height, Bitmap.Config.ARGB_8888
                )
                bitmapWithStride.copyPixelsFromBuffer(buffer)
                bitmap = Bitmap.createBitmap(bitmapWithStride, 0, 0, width, height)

//                fos = FileOutputStream("")
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
//                LogUtils.d(bitmap)
                tempBitmap = bitmap
                mediaProjection?.stop()
                captured = true
//                receiver?.send(RESULT_OK, Bundle())
                mediaProjection?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (fos != null) {
                try {
                    fos.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
//            bitmap?.recycle()
            bitmapWithStride?.recycle()
            image?.close()
        }
    }
    private var tempBitmap: Bitmap? = null

    private inner class MediaProjectionStopCallback : MediaProjection.Callback() {
        override fun onStop() {
            virtualDisplay?.release()
            imageReader?.setOnImageAvailableListener(null, null)
            mediaProjection?.unregisterCallback(this@MediaProjectionStopCallback)
        }
    }

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val width by lazy { ScreenUtils.getScreenWidth() }
    private val height by lazy { ScreenUtils.getScreenHeight() }
    private val virtualDisplay by lazy {
        mediaProjection?.createVirtualDisplay(
            "Screenshot",
            width,
            height,
            ScreenUtils.getScreenDensityDpi(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            imageReader?.surface,
            null,
            handler
        )
    }
    private val imageReader: ImageReader? by lazy {
        ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, 2
        )
    }
    private var mediaProjection: MediaProjection? = null
//    private var receiver: ResultReceiver? = null
    private lateinit var mediaProjectionManager: MediaProjectionManager

//https://github.com/npes87184/ScreenShareTile/blob/master/app/src/main/java/com/npes87184/screenshottile/ScreenshotService.kt
}
