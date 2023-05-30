package li.songe.gkd.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.blankj.utilcode.util.ToastUtils
import com.dylanc.activityresult.launcher.StartActivityLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.App
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.data.RuleManager
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.shizuku.Shell
import li.songe.gkd.shizuku.ShizukuShell
import java.io.File
import java.net.NetworkInterface
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume

object Ext {
    fun PackageManager.getApplicationInfoExt(
        packageName: String,
        value: Int = PackageManager.GET_META_DATA,
    ): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(value.toLong())
            )
        } else {
            @Suppress("DEPRECATION") getApplicationInfo(
                packageName,
                value
            )
        }
    }

    fun getAppName(appId: String? = null): String? {
        appId ?: return null
        return App.context.packageManager.getApplicationLabel(
            App.context.packageManager.getApplicationInfoExt(
                appId
            )
        ).toString()
    }

    fun Bitmap.isEmptyBitmap(): Boolean {
        val emptyBitmap = Bitmap.createBitmap(width, height, config)
        return this.sameAs(emptyBitmap)
    }

    suspend fun ImageReader.awaitImageAvailable(screenWidth: Int, screenHeight: Int): Bitmap =
        suspendCancellableCoroutine { block ->
            setOnImageAvailableListener({ imageReader ->

                val image = imageReader.acquireLatestImage()
                    ?: throw Error("get screen image result failed")

                val planes = image.planes
                val buffer = planes[0].buffer

                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding: Int = rowStride - pixelStride * screenWidth
                var bitmap =
                    Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    )
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                image.close()

                if (!bitmap.isEmptyBitmap()) {
                    setOnImageAvailableListener(null, null)
                    block.resume(bitmap)
                }
            }, Handler(Looper.getMainLooper()))

            block.invokeOnCancellation { setOnImageAvailableListener(null, null) }
        }

    fun createNotificationChannel(context: Service, notificationId: Int) {
        val channelId = "CHANNEL_ID"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_app_2)
            .setContentTitle("调试模式")
            .setContentText("正在录制您的屏幕内容")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setAutoCancel(false)

        val name = "调试模式"
        val descriptionText = "屏幕录制"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.createNotificationChannel(channel)
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            context.startForeground(notificationId, notification)
        }
    }

    fun getIpAddressInLocalNetwork(): Sequence<String> {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().iterator().asSequence()
        val localAddresses = networkInterfaces.flatMap {
            it.inetAddresses.asSequence().filter { inetAddress ->
                inetAddress.isSiteLocalAddress && !(inetAddress.hostAddress?.contains(":")
                    ?: false) && inetAddress.hostAddress != "127.0.0.1"
            }.map { inetAddress -> inetAddress.hostAddress }
        }
        return localAddresses
    }

    suspend fun getSubsFileLastModified(): Long {
        return RoomX.select<SubsItem>().map { File(it.filePath) }
            .filter { it.isFile && it.exists() }
            .maxOfOrNull { it.lastModified() } ?: -1L
    }

    suspend fun buildRuleManager(): RuleManager {
        return RuleManager(*RoomX.select<SubsItem>().sortedBy { it.index }.map { subsItem ->
            if (!subsItem.enable) return@map null
            try {
                val file = File(subsItem.filePath)
                if (file.isFile && file.exists()) {
                    return@map SubscriptionRaw.parse5(file.readText())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@map null
        }.filterNotNull().toTypedArray())
    }

    suspend fun getActivityIdByShizuku(): String? {
        if (!ShizukuShell.instance.isAvailable) return null
        val result = withTimeoutOrNull(250) {
            withContext(Dispatchers.IO) {
                ShizukuShell.instance.exec(Shell.Command("dumpsys activity activities | grep mResumedActivity"))
            }
        } ?: return null
        val strList = result.out.split("\u0020")
        if (!result.isSuccessful || strList.size < 4 || !strList[3].contains('/')) {
            return null
        }
        var (appId, activityId) = strList[3].split('/')
        if (activityId.startsWith('.')) {
            activityId = appId + activityId
        }
        return activityId
    }

    fun CoroutineScope.launchWhile(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ) = launch(context, start) {
        while (isActive) {
            block()
        }
    }

    fun CoroutineScope.launchTry(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ) = launch(context, start) {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            ToastUtils.showShort(e.message)
        }
    }


    fun createNotificationChannel(context: Service) {
        val channelId = "channel_service_ab"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_app_2)
            .setContentTitle("调试模式2")
            .setContentText("测试后台任务")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setAutoCancel(false)

        val name = "无障碍服务"
        val descriptionText = "无障碍服务保持活跃"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.createNotificationChannel(channel)
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startForeground(
                110,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            context.startForeground(110, notification)
        }
    }

    val LocalLauncher =
        compositionLocalOf<StartActivityLauncher> { error("not found StartActivityLauncher") }


    @Composable
    fun <T> usePollState(interval: Long = 400L, getter: () -> T): MutableState<T> {
        val mutableState = remember { mutableStateOf(getter()) }
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(interval)
                mutableState.value = getter()
            }
        }
        return mutableState
    }
}