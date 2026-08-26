package li.gkd.app.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import li.gkd.app.META
import li.gkd.app.app

enum class AppNotificationChannel(
    val id: String,
    private val label: String? = null,
    val description: String? = null,
    val importance: Int = NotificationManager.IMPORTANCE_LOW,
) {
    Service(id = "0"),
    Snapshot(id = "1", label = "保存快照通知");

    val displayName: String
        get() = label ?: META.appName
}

object NotificationChannels {
    fun initialize() {
        val manager = NotificationManagerCompat.from(app)
        val channelIds = AppNotificationChannel.entries.mapTo(mutableSetOf()) { it.id }

        manager.notificationChannels
            .filter { it.id !in channelIds }
            .forEach { manager.deleteNotificationChannel(it.id) }

        manager.createNotificationChannels(
            AppNotificationChannel.entries.map { spec ->
                NotificationChannel(
                    spec.id,
                    spec.displayName,
                    spec.importance,
                ).apply {
                    description = spec.description
                }
            }
        )
    }
}
