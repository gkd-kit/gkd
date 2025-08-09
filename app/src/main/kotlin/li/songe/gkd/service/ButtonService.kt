package li.songe.gkd.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.appScope
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.buttonNotif
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass

class ButtonService : OverlayWindowService() {
    override fun onClickView() = appScope.launchTry {
        SnapshotExt.captureSnapshot()
    }.let { }

    override val positionStoreKey = "overlay_xy_button"

    @Composable
    override fun ComposeContent() = AppTheme(invertedTheme = true) {
        val alpha = 0.75f
        Icon(
            imageVector = Icons.Default.CenterFocusWeak,
            contentDescription = null,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha))
                .size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
    }

    init {
        useAliveFlow(isRunning)
        useAliveToast("快照按钮服务")
        onCreated { buttonNotif.notifyService() }
        StopServiceReceiver.autoRegister()
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun start() {
            if (!canDrawOverlaysState.checkOrToast()) return
            startForegroundServiceByClass(ButtonService::class)
        }

        fun stop() = stopServiceByClass(ButtonService::class)
    }
}