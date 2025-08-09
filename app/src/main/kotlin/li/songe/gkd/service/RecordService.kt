package li.songe.gkd.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.recordNotif
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.theme.AppTheme
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass


class RecordService : OverlayWindowService() {
    val topAppInfoFlow by lazy {
        appInfoCacheFlow.combine(topActivityFlow) { map, topActivity ->
            map[topActivity.appId]
        }.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
    }

    override val positionStoreKey = "overlay_xy_record"

    @Composable
    override fun ComposeContent() = AppTheme(invertedTheme = true) {
        val bgColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(bgColor.copy(alpha = 0.9f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(bgColor)) {
                val topActivity = topActivityFlow.collectAsState().value
                Text(
                    text = topActivity.number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 2.dp),
                )
                Column {
                    topAppInfoFlow.collectAsState().value?.let {
                        AppNameText(appInfo = it)
                    }
                    Text(text = "${topActivity.appId}\n${topActivity.shortActivityId}")
                }
            }
        }
    }

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("记录服务")
        StopServiceReceiver.autoRegister()
        onCreated { recordNotif.notifyService() }
        onCreated {
            lifecycleScope.launch {
                topActivityFlow.collect {
                    recordNotif.copy(text = it.format()).notifyService()
                }
            }
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun start() {
            if (!canDrawOverlaysState.checkOrToast()) return
            startForegroundServiceByClass(RecordService::class)
        }

        fun stop() = stopServiceByClass(RecordService::class)
    }
}