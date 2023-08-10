package li.songe.gkd.debug

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.ServiceUtils
import com.torrydo.floatingbubbleview.FloatingBubble
import li.songe.gkd.app
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionFbService
import li.songe.gkd.composition.CompositionExt.useMessage
import li.songe.gkd.composition.InvokeMessage
import li.songe.gkd.util.SafeR

class FloatingService : CompositionFbService({
    useLifeCycleLog()
    val context = this
    val (onMessage, sendMessage) = useMessage(this::class.simpleName)

    onMessage { message ->
        when (message.method) {
            "showBubbles" -> context.showBubbles()
            "removeBubbles" -> context.removeBubbles()
        }
    }
    setupBubble { _, resolve ->
        val builder = FloatingBubble.Builder(this).bubble(SafeR.ic_capture, 40, 40)
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
            .setSmallIcon(SafeR.ic_launcher)
            .setContentTitle("搞快点")
            .setContentText("正在显示悬浮窗按钮")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun channelId() = "service-floating"
    override fun channelName() = "悬浮窗按钮服务"
    override fun notificationId() = 69

    companion object{
        fun isRunning() = ServiceUtils.isServiceRunning(FloatingService::class.java)
        fun stop(context: Context =app) {
            if (isRunning()) {
                context.stopService(Intent(context, FloatingService::class.java))
            }
        }
    }
}