package li.songe.gkd.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.debug.FloatingService
import li.songe.gkd.debug.HttpService
import li.songe.gkd.debug.ScreenshotService
import li.songe.gkd.permission.notificationState
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.componentName
import kotlin.reflect.KClass


data class Notif(
    val channelId: String = defaultChannel.id,
    val id: Int,
    val smallIcon: Int = SafeR.ic_status,
    val title: String = META.appName,
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
    val uri: String? = null,
    val stopService: KClass<out Service>? = null,
) {
    private fun toNotification(): Notification {
        val contextIntent = PendingIntent.getActivity(
            app,
            0,
            Intent().apply {
                component = MainActivity::class.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                data = uri?.toUri()
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val deleteIntent = stopService?.let {
            PendingIntent.getBroadcast(
                app,
                0,
                Intent().apply {
                    action = StopServiceReceiver.STOP_ACTION
                    putExtra(StopServiceReceiver.STOP_ACTION, it.componentName.className)
                    setPackage(META.appId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val notification = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contextIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .build()
        return notification
    }

    fun notifySelf() {
        if (!notificationState.updateAndGet()) return
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(app).notify(id, toNotification())
    }

    fun notifyService(context: Service) {
        ServiceCompat.startForeground(
            context,
            id,
            toNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            } else {
                -1
            }
        )
    }
}

val abNotif by lazy {
    Notif(
        id = 100,
        text = "无障碍正在运行",
        ongoing = true,
        autoCancel = false,
    )
}

val screenshotNotif by lazy {
    Notif(
        id = 101,
        text = "截屏服务正在运行",
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
        stopService = ScreenshotService::class,
    )
}

val floatingNotif by lazy {
    Notif(
        id = 102,
        text = "悬浮按钮正在显示",
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
        stopService = FloatingService::class,
    )
}

val httpNotif by lazy {
    Notif(
        id = 103,
        text = "HTTP服务正在运行",
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
        stopService = HttpService::class,
    )
}

val snapshotNotif by lazy {
    Notif(
        id = 104,
        text = "快照已保存至记录",
        ongoing = false,
        autoCancel = true,
        uri = "gkd://page/2",
    )
}

val snapshotActionNotif by lazy {
    Notif(
        id = 105,
        text = "快照服务正在运行",
        ongoing = true,
        autoCancel = false,
    )
}