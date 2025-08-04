package li.songe.gkd.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import li.songe.gkd.app

sealed class NotifChannel(
    val id: String,
    val name: String,
    val desc: String? = null,
) {
    data object Default : NotifChannel(
        id = "0",
        name = "GKD",
    )

    data object Snapshot : NotifChannel(
        id = "1",
        name = "保存快照通知",
    )
}

fun initChannel() {
    val channels = arrayOf(NotifChannel.Default, NotifChannel.Snapshot)
    val manager = NotificationManagerCompat.from(app)
    // delete old channels
    manager.notificationChannels.filter { channels.none { c -> c.id == it.id } }.forEach {
        manager.deleteNotificationChannel(it.id)
    }
    // create/update new channels
    channels.forEach {
        val channel = NotificationChannel(
            it.id,
            it.name,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = it.desc
        }
        manager.createNotificationChannel(channel)
    }
}
