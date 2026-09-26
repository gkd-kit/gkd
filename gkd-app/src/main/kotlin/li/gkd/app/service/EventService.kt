package li.gkd.app.service

import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.appScope
import li.gkd.db.A11yEventLog
import li.gkd.app.data.toA11yEventLog
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.ui.component.GkEventLogOverlayCard
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.launchLogged
import li.gkd.db.Db
import kotlin.time.Duration.Companion.milliseconds
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.rememberLazyListAutoFollowState

class EventService : OverlayWindowService(positionKey = "event") {

    private val eventLogs = mutableStateListOf<A11yEventLog>()
    private var minimized by mutableStateOf(false)

    override fun isViewClickEnabled(): Boolean = minimized

    override fun onClickView() {
        minimized = false
    }

    @Composable
    override fun ComposeContent() {
        if (minimized) {
            val alpha = 0.75f
            GkIcon(
                imageVector = GkIcons.ExpandContent,
                contentDescription = UiStrings.event_log_window_restore,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha))
                    .semantics {
                        onClick(label = UiStrings.event_log_window_restore) {
                            minimized = false
                            true
                        }
                    }
                    .size(40.dp)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
        } else {
            val bgColor = MaterialTheme.colorScheme.surface
            CompositionLocalProvider(
                LocalContentColor provides contentColorFor(bgColor),
            ) {
                val latestEventId = eventLogs.lastOrNull()?.id ?: 0
                val followState = rememberLazyListAutoFollowState(
                    itemCount = eventLogs.size,
                    latestItemKey = latestEventId,
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(bgColor.copy(alpha = 0.9f))
                        .width(256.dp)
                        .padding(4.dp)
                ) {
                    ClosableTitle(
                        title = if (A11yService.isRunning.collectAsStateWithLifecycle().value || uiAutomationFlow.collectAsStateWithLifecycle().value != null) UiStrings.event_service_label else UiStrings.event_service_no_permission,
                        onMinimizeRequest = { minimized = true },
                        minimizeContentDescription = UiStrings.event_log_window_minimize,
                    )
                    val textStyle = MaterialTheme.typography.labelSmall
                    CompositionLocalProvider(
                        LocalTextStyle provides textStyle,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = followState.listState,
                                contentPadding = PaddingValues(bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(eventLogs, { it.id }) {
                                    GkEventLogOverlayCard(
                                        eventLog = it,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                            if (eventLogs.isNotEmpty() && !followState.isAutoFollowEnabled) {
                                val count = (latestEventId - followState.pausedAtItemKey)
                                    .coerceAtLeast(0)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .width(IntrinsicSize.Min),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (count > 0) {
                                        Text(text = UiStrings.event_pending_count(count))
                                    }
                                    GkIconButton(
                                        imageVector = GkIcons.ArrowDownward,
                                        onClick = followState::resume,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private val tempEventListFlow = MutableStateFlow(emptyList<A11yEventLog>())

    private suspend fun flushEventLogs() = withContext(NonCancellable) {
        val list = tempEventListFlow.getAndUpdate { emptyList() }
        if (list.isNotEmpty()) {
            Db.a11yEventLogDao.insert(list)
        }
    }

    init {
        useLogLifecycle()
        onCreated {
            logAutoId = 0
            instance = this@EventService
            lifecycleScope.launch {
                logAutoId = (Db.a11yEventLogDao.maxId() ?: 0).coerceAtLeast(1)
            }
            lifecycleScope.launch {
                try {
                    while (isActive) {
                        delay(1000.milliseconds)
                        flushEventLogs()
                    }
                } finally {
                    flushEventLogs()
                }
            }
            NotificationCatalog.event().startForeground()
        }
        onDestroyed {
            instance = null
            logAutoId = 0
        }
        useServicePresence(
            stateFlow = isRunning,
            name = UiStrings.event_service_label,
        )
    }

    companion object {
        private var instance: EventService? = null
        private var logAutoId = 0

        fun logEvent(event: AccessibilityEvent) {
            val service = instance ?: return
            if (event.packageName == META.appId) return
            if (logAutoId == 0) return
            logAutoId++
            val eventLog = event.toA11yEventLog(logAutoId)
            service.eventLogs.add(eventLog)
            service.tempEventListFlow.update { it + eventLog }
            if (service.eventLogs.size >= 256) {
                service.eventLogs.removeRange(0, 64)
            }
            if (eventLog.id % 100 == 0) {
                appScope.launchLogged { Db.a11yEventLogDao.deleteKeepLatest() }
            }
        }

        val isRunning: StateFlow<Boolean>
            field = MutableStateFlow(false)
        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            IntentUtils.startForegroundServiceByClass(EventService::class)
        }

        fun stop() = IntentUtils.stopServiceByClass(EventService::class)

    }
}
