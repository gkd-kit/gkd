package li.songe.gkd.service

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import li.songe.gkd.a11y.topActivityFlow
import li.songe.gkd.a11y.updateTopActivity
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.recordNotif
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.shizuku.SafeTaskListener
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.style.iconTextSize
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.copyText
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass


class ActivityService : OverlayWindowService(
    positionKey = "activity"
) {

    val topAppInfoFlow by lazy {
        combine(appInfoMapFlow, topActivityFlow) { map, topActivity ->
            map[topActivity.appId]
        }.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
    }

    val activityOkFlow by lazy {
        combine(A11yService.isRunning, shizukuContextFlow) { a, b ->
            a || SafeTaskListener.isAvailable
        }.stateIn(scope = lifecycleScope, started = SharingStarted.Eagerly, initialValue = false)
    }

    @Composable
    override fun ComposeContent() {
        val bgColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(bgColor.copy(alpha = 0.9f))
                .padding(4.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(bgColor)) {
                if (activityOkFlow.collectAsState().value) {
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
                        RowText(text = topActivity.appId)
                        topActivity.shortActivityId?.let {
                            RowText(text = it)
                        }
                    }
                } else {
                    Column {
                        Text(text = "记录服务")
                        Text(text = "无权限检测界面切换")
                    }
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
            if (!A11yService.isRunning.value) {
                shizukuContextFlow.value.topCpn()?.let { cpn ->
                    updateTopActivity(
                        appId = cpn.packageName,
                        activityId = cpn.className,
                        type = 2,
                    )
                }
            }
        }
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun start() {
            if (!canDrawOverlaysState.checkOrToast()) return
            startForegroundServiceByClass(ActivityService::class)
        }

        fun stop() = stopServiceByClass(ActivityService::class)
    }
}

@Composable
private fun RowText(text: String) {
    Row {
        Text(text = text, modifier = Modifier.weight(1f, false))
        Spacer(modifier = Modifier.width(4.dp))
        PerfIcon(
            imageVector = PerfIcon.ContentCopy,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable(onClick = {
                    copyText(text)
                })
                .iconTextSize(),
        )
    }
}
