package li.songe.gkd.util

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import kotlinx.coroutines.suspendCancellableCoroutine
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import java.net.NetworkInterface
import kotlin.coroutines.resume


object Ext {
    fun PackageManager.getApplicationInfoExt(
        packageName: String,
        value: Int = PackageManager.GET_META_DATA,
    ): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplicationInfo(
                packageName, PackageManager.ApplicationInfoFlags.of(value.toLong())
            )
        } else {
            @Suppress("DEPRECATION") getApplicationInfo(
                packageName, value
            )
        }
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
                var bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888
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
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId).setSmallIcon(SafeR.ic_launcher)
            .setContentTitle("调试模式").setContentText("正在录制您的屏幕内容")
            .setContentIntent(pendingIntent).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true).setAutoCancel(false)

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
                notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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

    fun createNotificationChannel(context: Service) {
//        通知渠道
        val channelId = "无障碍服务"
        val name = "无障碍服务"
        val desc = "显示无障碍服务状态"

        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance)
        channel.description = desc
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.createNotificationChannel(channel)

        val icon = SafeR.ic_launcher
        val title = "搞快点"
        val text = "无障碍正在运行"
        val id = 100
        val ongoing = true
        val autoCancel = false

        notificationManager.cancel(id)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder =
            NotificationCompat.Builder(context, channelId).setSmallIcon(icon).setContentTitle(title)
                .setContentText(text).setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).setOngoing(ongoing)
                .setAutoCancel(autoCancel)

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startForeground(
                id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            )
        } else {
            context.startForeground(id, notification)
        }

    }

}

fun Context.setExcludeFromRecents(enable: Boolean) {
    (this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { manager ->
        manager.appTasks.forEach { task ->
            task?.setExcludeFromRecents(enable)
        }
    }
}
