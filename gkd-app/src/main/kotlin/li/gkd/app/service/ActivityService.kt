package li.gkd.app.service

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.A11yState
import li.gkd.app.a11y.ActivityScene
import li.gkd.app.a11y.topActivityFlow
import li.gkd.app.a11y.currentTopActivity
import li.gkd.app.a11y.updateTopActivity
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.IntentUtils
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons


class ActivityService : OverlayWindowService(
    positionKey = "activity"
) {
    private val activityOkFlow by lazy {
        combine(A11yService.isRunning, privilegeContextFlow) { a, b ->
            a || b != null
        }.stateIn(scope = lifecycleScope, started = SharingStarted.Eagerly, initialValue = false)
    }

    @Composable
    override fun ComposeContent() {
        val bgColor = MaterialTheme.colorScheme.surface
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(bgColor.copy(alpha = 0.9f))
                .width(IntrinsicSize.Max)
                .padding(4.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(bgColor)) {
                val topActivity by topActivityFlow.collectAsStateWithLifecycle(initialValue = currentTopActivity)
                val hasAuth by activityOkFlow.collectAsStateWithLifecycle()
                ClosableTitle(
                    title = if (hasAuth) UiStrings.activity_record_service else UiStrings.activity_record_service_no_permission
                )
                if (hasAuth) {
                    Box {
                        Column(
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            RowText(text = topActivity.appId)
                            RowText(
                                text = topActivity.shortActivityId,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (topActivity.number > 0) {
                            Text(
                                text = topActivity.number.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .zIndex(1f)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .padding(end = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    init {
        useLogLifecycle()
        useServicePresence(
            stateFlow = isRunning,
            name = UiStrings.activity_record_service,
        )
        onCreated {
            NotificationCatalog.activity().startForeground()
            lifecycleScope.launch {
                topActivityFlow.collect {
                    NotificationCatalog.activity(text = it.format()).startForeground()
                }
            }
            if (!A11yService.isRunning.value) {
                A11yState.withTopActivityLock {
                    privilegeContextFlow.value?.run {
                        topCpn()?.let { cpn ->
                            updateTopActivity(
                                appId = cpn.packageName,
                                activityId = cpn.className,
                                scene = ActivityScene.TaskStack,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)
        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            IntentUtils.startForegroundServiceByClass(ActivityService::class)
        }

        fun stop() = IntentUtils.stopServiceByClass(ActivityService::class)

    }
}

@Composable
private fun RowText(text: String?, color: Color = Color.Unspecified) {
    Row {
        Text(text = text ?: "null", color = color, modifier = Modifier.weight(1f, false))
        if (text != null) {
            Spacer(modifier = Modifier.width(4.dp))
            GkIcon(
                imageVector = GkIcons.ContentCopy,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = {
                        copyText(text)
                    })
                    .iconTextSize(),
            )
        }
    }
}
