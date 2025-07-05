package li.songe.gkd.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import li.songe.gkd.app

data class NotifChannel(
    val id: String,
    val name: String,
    val desc: String,
)

val defaultChannel by lazy {
    NotifChannel(
        id = "default",
        name = "GKD",
        desc = "默认通知渠道",
    )
}

private fun createChannel(notifChannel: NotifChannel) {
    val channel = NotificationChannel(
        notifChannel.id,
        notifChannel.name,
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = notifChannel.desc
    }
    NotificationManagerCompat.from(app).createNotificationChannel(channel)
}

fun initChannel() {
    createChannel(defaultChannel)

    // delete old channels
    val manager = NotificationManagerCompat.from(app)
    manager.notificationChannels.filter { it.id != defaultChannel.id }.forEach {
        manager.deleteNotificationChannel(it.id)
    }
}
