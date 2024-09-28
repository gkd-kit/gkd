package li.songe.gkd.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import li.songe.gkd.MainActivity
import li.songe.gkd.app
import li.songe.gkd.permission.notificationState

fun createChannel(context: Context, notifChannel: NotifChannel) {
    val importance = NotificationManager.IMPORTANCE_LOW
    val channel = NotificationChannel(notifChannel.id, notifChannel.name, importance)
    channel.description = notifChannel.desc
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.createNotificationChannel(channel)
}

private fun Notif.toNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
        app,
        0,
        Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            uri?.let { data = Uri.parse(it) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(app, channel.id)
        .setSmallIcon(smallIcon)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pendingIntent)
        .setOngoing(ongoing)
        .setAutoCancel(autoCancel)
        .build()
    return notification
}

@SuppressLint("MissingPermission")
fun Notif.notify() {
    if (notificationState.updateAndGet()) {
        NotificationManagerCompat.from(app).notify(id, toNotification())
    }
}

fun Notif.notifyService(context: Service) {
    val notification = toNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.startForeground(
            id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )
    } else {
        context.startForeground(id, notification)
    }
}
