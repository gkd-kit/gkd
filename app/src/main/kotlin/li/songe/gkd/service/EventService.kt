package li.songe.gkd.service

import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.appScope
import li.songe.gkd.data.A11yEventLog
import li.songe.gkd.data.toA11yEventLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.notif.StopServiceReceiver
import li.songe.gkd.notif.eventNotif
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.ui.EventLogCard
import li.songe.gkd.ui.component.LocalNumberCharWidth
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.isAtBottom
import li.songe.gkd.ui.component.measureNumberTextWidth
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.startForegroundServiceByClass
import li.songe.gkd.util.stopServiceByClass

class EventService : OverlayWindowService(positionKey = "event") {

    val eventLogs = mutableStateListOf<A11yEventLog>()
    private var tempEventId = 0
    private var firstToBottom = false

    @Composable
    override fun ComposeContent() {
        val bgColor = MaterialTheme.colorScheme.surface
        CompositionLocalProvider(
            LocalContentColor provides contentColorFor(bgColor),
        ) {
            val listState = key(eventLogs.isEmpty()) { rememberLazyListState() }
            val isAtBottom by listState.isAtBottom()
            val subScope = rememberCoroutineScope()
            SideEffect {
                val latestId = eventLogs.lastOrNull()?.id ?: 0
                if (tempEventId != latestId) {
                    tempEventId = latestId
                    if (isAtBottom) {
                        subScope.launch { listState.scrollToItem(eventLogs.lastIndex) }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(bgColor.copy(alpha = 0.9f))
                    .width(256.dp)
                    .padding(4.dp)
            ) {
                ClosableTitle(
                    title = if (A11yService.isRunning.collectAsState().value) "事件服务" else "事件服务(无权限)"
                )
                val textStyle = MaterialTheme.typography.labelSmall
                val numCharWidth = measureNumberTextWidth(textStyle)
                CompositionLocalProvider(
                    LocalTextStyle provides textStyle,
                    LocalNumberCharWidth provides numCharWidth,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(eventLogs, { it.id }) {
                                EventLogCard(
                                    eventLog = it,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                        if (eventLogs.isNotEmpty() && !isAtBottom) {
                            if (!firstToBottom) {
                                firstToBottom = true
                                SideEffect {
                                    subScope.launch { listState.scrollToItem(eventLogs.lastIndex) }
                                }
                            }
                            var count by remember { mutableIntStateOf(-1) }
                            LaunchedEffect(eventLogs.last().id) { count++ }
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
                                    onClick = {
                                        subScope.launch {
                                            listState.scrollToItem(eventLogs.lastIndex)
                                        }
                                    },
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
                delay(1000)
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
        onCreated { eventNotif.notifyService() }
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
            if (!canDrawOverlaysState.checkOrToast()) return
            startForegroundServiceByClass(EventService::class)
        }

        fun stop() = stopServiceByClass(EventService::class)
    }
}
