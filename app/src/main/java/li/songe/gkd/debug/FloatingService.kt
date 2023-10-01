package li.songe.gkd.debug

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.ServiceUtils
import com.blankj.utilcode.util.ToastUtils
import com.torrydo.floatingbubbleview.FloatingBubble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionFbService
import li.songe.gkd.notif.floatingChannel
import li.songe.gkd.notif.floatingNotif
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchTry
import kotlin.math.sqrt


class FloatingService : CompositionFbService({
    var lastX = 0.0F
    var lastY = 0.0F
    var downTime = 0L
    useLifeCycleLog()
    setupBubble { _, resolve ->
        val builder = FloatingBubble.Builder(this).bubble {
            Icon(painter = painterResource(SafeR.ic_capture),
                contentDescription = "capture",
                modifier = Modifier
                    .clickable(indication = null,
                        interactionSource = remember { MutableInteractionSource() }) {
                        appScope.launchTry(Dispatchers.IO) {
                            SnapshotExt.captureSnapshot()
                            ToastUtils.showShort("快照成功")
                        }
                    }
                    .size(40.dp),
                tint = Color.Red)
        }.enableCloseBubble(false).enableAnimateToEdge(false)
            .addFloatingBubbleListener(object : FloatingBubble.Listener {
                override fun onUp(x: Float, y: Float) {
                    val now = System.currentTimeMillis()
                    appScope.launchTry(Dispatchers.IO) {
                        if (now - downTime < 300 && sqrt((lastX - x) * (lastX - x) + (lastY - y) * (lastY - y)) < 50) { // 计算移动距离是否小于50像素
                            SnapshotExt.captureSnapshot()
                            ToastUtils.showShort("快照成功")
                        }
                        lastX = x
                        lastY = y
                    }
                }

                override fun onDown(x: Float, y: Float) {
                    downTime = System.currentTimeMillis()
                }
            })
        resolve(builder)
    }
}) {


    override fun initialNotification(): Notification {
        return NotificationCompat.Builder(this, floatingChannel.id).setOngoing(true)
            .setSmallIcon(SafeR.ic_launcher).setContentTitle(floatingNotif.title)
            .setContentText(floatingNotif.text).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(Notification.CATEGORY_SERVICE).build()
    }

    override fun notificationId() = floatingNotif.id

    override fun createNotificationChannel(channelId: String, channelName: String) {
//        by app init
    }

    companion object {
        fun isRunning() = ServiceUtils.isServiceRunning(FloatingService::class.java)
        fun stop(context: Context = app) {
            if (isRunning()) {
                context.stopService(Intent(context, FloatingService::class.java))
            }
        }
    }
}