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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.gkd.app.META
import li.gkd.app.MainViewModel
import li.gkd.app.appScope
import li.gkd.app.data.A11yEventLog
import li.gkd.app.data.toA11yEventLog
import li.gkd.app.db.DbSet
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.notif.StopServiceReceiver
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.ui.EventLogCard
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.rememberLazyListAutoFollowState
import li.gkd.app.util.launchTry
import li.gkd.app.util.startForegroundServiceByClass
import li.gkd.app.util.stopServiceByClass
import kotlin.time.Duration.Companion.milliseconds

class EventService : OverlayWindowService(positionKey = "event") {

    val eventLogs = mutableStateListOf<A11yEventLog>()

    @Composable
    override fun ComposeContent() {
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
                    title = if (A11yService.isRunning.collectAsStateWithLifecycle().value || uiAutomationFlow.collectAsStateWithLifecycle().value != null) "事件服务" else "事件服务(无权限)"
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
                                EventLogCard(
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
                                    Text(text = "+$count")
                                }
                                PerfIconButton(
                                    imageVector = PerfIcon.ArrowDownward,
                                    onClick = followState::resume,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val tempEventListFlow = MutableStateFlow(emptyList<A11yEventLog>()).apply {
        appScope.launch {
            while (scope.isActive) {
                delay(1000.milliseconds)
                val list = getAndUpdate { emptyList() }
                if (list.isNotEmpty()) {
                    DbSet.a11yEventLogDao.insert(list)
                }
            }
        }
    }

    init {
        logAutoId = 0
        instance = this
        onDestroyed {
            instance = null
            logAutoId = 0
        }
        scope.launch {
            logAutoId = (DbSet.a11yEventLogDao.maxId() ?: 0).coerceAtLeast(1)
        }

        useLogLifecycle()
        useAliveFlow(isRunning)
        useAliveToast("事件服务")
        StopServiceReceiver.autoRegister()
        onCreated {
            NotificationCatalog.event().startForeground()
        }
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
                appScope.launchTry { DbSet.a11yEventLogDao.deleteKeepLatest() }
            }
        }

        val isRunning = MutableStateFlow(false)
        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            startForegroundServiceByClass(EventService::class)
        }

        fun stop() = stopServiceByClass(EventService::class)

        suspend fun setEnabled(mainVm: MainViewModel, enabled: Boolean) {
            if (!enabled) {
                stop()
                return
            }
            if (!mainVm.permissionRequests.ensurePermissions(
                    PermissionStates.foregroundServiceSpecialUse,
                    PermissionStates.notification,
                    PermissionStates.drawOverlays,
                )
            ) return
            start()
        }
    }
}
