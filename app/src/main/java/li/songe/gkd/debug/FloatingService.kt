package li.songe.gkd.debug

import android.content.Context
import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ServiceUtils
import com.blankj.utilcode.util.ToastUtils
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionFbService
import li.songe.gkd.data.Tuple3
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.floatingChannel
import li.songe.gkd.notif.floatingNotif
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchTry
import kotlin.math.sqrt

class FloatingService : CompositionFbService({
    useLifeCycleLog()

    configBubble { resolve ->
        val builder = BubbleBuilder(this).bubbleCompose {
            Icon(
                painter = painterResource(SafeR.ic_capture),
                contentDescription = "capture",
                modifier = Modifier.size(40.dp),
                tint = Color.Red
            )
        }.enableAnimateToEdge(false)

        // https://github.com/gkd-kit/gkd/issues/62
        // https://github.com/gkd-kit/gkd/issues/61
        val defaultFingerData = Tuple3(0L, 0f, 0f)
        var fingerDownData = defaultFingerData
        val maxDistanceOffset = 50
        builder.addFloatingBubbleListener(object : FloatingBubbleListener {
            override fun onFingerDown(x: Float, y: Float) {
                fingerDownData = Tuple3(System.currentTimeMillis(), x, y)
            }

            override fun onFingerMove(x: Float, y: Float) {
                if (fingerDownData === defaultFingerData) {
                    return
                }
                val dx = fingerDownData.t1 - x
                val dy = fingerDownData.t2 - y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > maxDistanceOffset) {
                    // reset
                    fingerDownData = defaultFingerData
                }
            }

            override fun onFingerUp(x: Float, y: Float) {
                if (System.currentTimeMillis() - fingerDownData.t0 < ViewConfiguration.getTapTimeout()) {
                    // is onClick
                    appScope.launchTry(Dispatchers.IO) {
                        SnapshotExt.captureSnapshot()
                        ToastUtils.showShort("快照成功")
                    }
                }
            }
        })
        resolve(builder)
    }
}) {

    override fun onCreate() {
        super.onCreate()
        minimize()
    }

    override fun startNotificationForeground() {
        createNotif(this, floatingChannel.id, floatingNotif)
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