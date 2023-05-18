package li.songe.gkd.debug

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.ServiceUtils
import com.torrydo.floatingbubbleview.FloatingBubble
import li.songe.gkd.App
import li.songe.gkd.R
import li.songe.gkd.composition.CompositionFbService
import li.songe.gkd.composition.CompositionExt.useMessage
import li.songe.gkd.composition.InvokeMessage
import li.songe.gkd.debug.server.HttpService

class FloatingService : CompositionFbService({
    val context = this
    val (onMessage, sendMessage) = useMessage(this::class.simpleName)

    onMessage { message ->
        when (message.method) {
            "showBubbles" -> context.showBubbles()
            "removeBubbles" -> context.removeBubbles()
        }
    }

    setupBubble { _, resolve ->
        val builder = FloatingBubble.Builder(this).bubble(R.drawable.capture, 40, 40)
            .enableCloseBubble(false)
            .addFloatingBubbleListener(object : FloatingBubble.Listener {
                override fun onClick() {
                    sendMessage(InvokeMessage(HttpService::class.simpleName, "capture"))
                }
            })
        resolve(builder)
    }
}) {

    override fun setupNotificationBuilder(channelId: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_app_2)
            .setContentTitle("bubble is running")
            .setContentText("click to do nothing")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun channelId() = "your_channel_id"
    override fun channelName() = "your_channel_name"
    override fun notificationId() = 69

    companion object{
        fun isRunning() = ServiceUtils.isServiceRunning(FloatingService::class.java)
        fun stop(context: Context = App.context) {
            if (isRunning()) {
                context.stopService(Intent(context, FloatingService::class.java))
            }
        }
    }
}