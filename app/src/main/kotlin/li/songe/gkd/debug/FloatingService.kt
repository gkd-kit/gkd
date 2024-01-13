package li.songe.gkd.debug

import android.content.Context
import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionFbService
import li.songe.gkd.data.Tuple3
import li.songe.gkd.notif.createNotif
import li.songe.gkd.notif.floatingChannel
import li.songe.gkd.notif.floatingNotif
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.toast
import kotlin.math.sqrt

class FloatingService : CompositionFbService({
    useLifeCycleLog()
    configBubble { resolve ->
        val builder = BubbleBuilder(this).bubbleCompose {
            Icon(
                imageVector = Icons.Default.CenterFocusWeak,
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
                        toast("快照成功")
                    }
                }
            }
        })
        resolve(builder)
    }

    isRunning.value = true
    onDestroy {
        isRunning.value = false
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
        val isRunning = MutableStateFlow(false)
        fun stop(context: Context = app) {
            context.stopService(Intent(context, FloatingService::class.java))
        }
    }
}