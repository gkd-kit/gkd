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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.foregroundRulesFlow
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.resources.*
import li.gkd.app.rule.TopActivity
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.stringResource

class ActivityService :
    OverlayWindowService(
        positionKey = "activity",
    ) {
    private val recordedActivityFlow by lazy {
        combine(
                AndroidForegroundState.observedForegroundFlow,
                foregroundRulesFlow,
                uiAutomationFlow,
                A11yService.isRunning,
                privilegeContextFlow,
            ) { observation, rules, automation, accessibility, privilege ->
                val nodesAvailable = automation != null || accessibility
                val identity =
                    if (nodesAvailable) observation.confirmed
                    else observation.task.takeIf { privilege != null }
                val activity = identity?.let {
                    rules.topActivity.takeIf { it.sameAs(identity.appId, identity.activityId) }
                        ?: TopActivity(identity.appId, identity.activityId)
                }
                RecordedActivity(
                    activity = activity,
                    taskStack = !nodesAvailable && privilege != null,
                    hasAuth = accessibility || privilege != null,
                )
            }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, RecordedActivity())
    }

    private data class RecordedActivity(
        val activity: TopActivity? = null,
        val taskStack: Boolean = false,
        val hasAuth: Boolean = false,
    )

    @Composable
    override fun ComposeContent() {
        val bgColor = MaterialTheme.colorScheme.surface
        Column(
            modifier =
                Modifier.clip(MaterialTheme.shapes.small)
                    .background(bgColor.copy(alpha = 0.9f))
                    .width(IntrinsicSize.Max)
                    .padding(4.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(bgColor)) {
                val recorded by recordedActivityFlow.collectAsStateWithLifecycle()
                val topActivity = recorded.activity
                ClosableTitle(
                    title =
                        stringResource(
                            if (recorded.hasAuth) Res.string.activity_record_service
                            else Res.string.activity_record_service_no_permission,
                        ),
                    label =
                        if (recorded.taskStack)
                            stringResource(Res.string.activity_record_task_stack)
                        else null,
                )
                if (recorded.hasAuth) {
                    Box {
                        Column(
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            RowText(text = topActivity?.appId ?: stringResource(Res.string.unknown))
                            RowText(
                                text = topActivity?.shortActivityId,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if ((topActivity?.number ?: 0) > 0) {
                            Text(
                                text = topActivity?.number.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier =
                                    Modifier.align(Alignment.TopEnd)
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
            name = Res.string.activity_record_service,
        )
        onCreated {
            NotificationCatalog.activity().startForeground()
            lifecycleScope.launch {
                recordedActivityFlow
                    .map { it.activity?.format() }
                    .distinctUntilChanged()
                    .collect { text ->
                        NotificationCatalog.activity(
                                text = text,
                            )
                            .startForeground()
                    }
            }
        }
    }

    companion object {
        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)

        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            IntentUtils.startForegroundService(ActivityService::class)
        }

        fun stop() = IntentUtils.stopService(ActivityService::class)
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
                modifier =
                    Modifier.clip(MaterialTheme.shapes.extraSmall)
                        .clickable(
                            onClick = {
                                copyText(text)
                            },
                        )
                        .iconTextSize(),
            )
        }
    }
}
