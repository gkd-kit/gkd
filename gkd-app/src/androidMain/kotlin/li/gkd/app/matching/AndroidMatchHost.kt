package li.gkd.app.matching

import android.graphics.Rect
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.gkd.app.META
import li.gkd.app.matching.action.ActionPerformer
import li.gkd.app.matching.diagnostics.MatchEventBatch
import li.gkd.app.matching.diagnostics.MatchEventDrop
import li.gkd.app.matching.diagnostics.MatchNodeProvenance
import li.gkd.app.matching.diagnostics.MatchNodeReadReason
import li.gkd.app.matching.diagnostics.MatchNodeRole
import li.gkd.app.matching.diagnostics.MatchNodeSource
import li.gkd.app.matching.diagnostics.MatchNodeSummary
import li.gkd.app.matching.diagnostics.MatchServiceEvent
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.MatchWindowEvent
import li.gkd.app.matching.diagnostics.diagnosticText
import li.gkd.app.matching.input.A11yEvent
import li.gkd.app.matching.input.ActivityScene
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.CONTENT_CHANGED
import li.gkd.app.matching.input.ForegroundIdentity
import li.gkd.app.matching.input.ForegroundWindowTracker
import li.gkd.app.matching.input.MatchEventKey
import li.gkd.app.matching.input.MatchEventKind
import li.gkd.app.matching.input.MatchEventQueue
import li.gkd.app.matching.input.STATE_CHANGED
import li.gkd.app.matching.input.appChangeTime
import li.gkd.app.matching.input.currentTopActivity
import li.gkd.app.matching.input.foregroundRulesFlow
import li.gkd.app.matching.input.imeAppId
import li.gkd.app.matching.input.isActivity
import li.gkd.app.matching.input.isInteractive
import li.gkd.app.matching.input.isUseful
import li.gkd.app.matching.input.lastTriggerTime
import li.gkd.app.matching.input.launcherAppId
import li.gkd.app.matching.input.toA11yEvent
import li.gkd.app.matching.input.updateTopActivity
import li.gkd.app.matching.query.A11yQueryContext
import li.gkd.app.matching.query.MatchNodeReadUnavailable
import li.gkd.app.matching.query.RuleQueryNodes
import li.gkd.app.matching.query.isExpired
import li.gkd.app.matching.query.queryRules
import li.gkd.app.matching.query.readA11yWindow
import li.gkd.app.matching.query.setGeneratedTime
import li.gkd.app.model.ActionResult
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.record.addActionLog
import li.gkd.app.rule.*
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.service.EventService
import li.gkd.app.service.topAppIdFlow
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.snapshot.captureScreenshotOnEvent
import li.gkd.app.subscription.refreshSubscriptionsOnLauncher
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AndroidToastUtils
import li.gkd.app.util.monotonicTimeMillis

private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val diagnosticConnectionIds = AtomicLong()
private val diagnosticReadIds = AtomicLong()
private val diagnosticEventIds = AtomicLong()

class AndroidMatchHost(private val service: AndroidAutomationSession) {
    val connectionId = diagnosticConnectionIds.incrementAndGet()
    // This boundary belongs to the service connection, not to a diagnostic capture.
    private var firstQueryStarted = false
    private var querySource: PageNode? = null
    private var queryRoot: PageNode? = null
    private val windows = ForegroundWindowTracker()
    private val a11yContext =
        A11yQueryContext(
            getRoot = { queryRootNode() },
            onNodeUsed = ::observeQueryNode,
            diagnostics = MatchingRuntime.coordinator.diagnostics,
            nodeReader = MatchingRuntime.nodeReader,
        )
    private val queryNodes =
        object : RuleQueryNodes<AccessibilityNodeInfo> {
            override suspend fun root() =
                queryRootNode().also {
                    if (it == null) requestRoot(MatchNodeReadReason.MissingOrExpired)
                }

            override fun appId(node: AccessibilityNodeInfo) = node.packageName?.toString()

            override suspend fun refresh(node: AccessibilityNodeInfo) =
                a11yContext.refresh(node).also {
                    querySource = it?.let { refreshed ->
                        observedPageNode(
                            AndroidForegroundState.currentPage,
                            refreshed,
                            MatchNodeSource.Refresh,
                            MatchNodeReadReason.SourceReused,
                            querySource?.provenance?.readId,
                        )
                    }
                }

            override suspend fun match(rule: ResolvedRule, node: AccessibilityNodeInfo) =
                MatchingRuntime.nodeReader.query {
                    a11yContext.queryRule(rule, node, coordinator.diagnostics)
                }

            override suspend fun perform(
                rule: ResolvedRule,
                node: AccessibilityNodeInfo,
            ): ActionResult {
                if (!isActionContextCurrent(node)) {
                    throw CancellationException("action_window_changed")
                }
                return ActionPerformer.getAction(
                        rule.parameters.action
                            ?: rule.parameters.position?.let {
                                ActionPerformer.ClickCenter.action
                            }
                            ?: rule.parameters.swipeArg?.let {
                                ActionPerformer.Swipe.action
                            },
                    )
                    .perform(node, rule.parameters, coordinator.diagnostics.enabled)
            }
        }

    private fun isActionContextCurrent(node: AccessibilityNodeInfo): Boolean =
        AndroidForegroundState.withTopActivityLock {
            a11yContext.queryPage === AndroidForegroundState.currentPage &&
                !AndroidForegroundState.foregroundPending &&
                windows.isCurrentKnownWindow(node.windowId, node.packageName?.toString())
        }

    private val coordinator
        get() = MatchingRuntime.coordinator

    private val timing
        get() = coordinator.policy.value

    private val effective
        get() = AndroidAutomationSessions.isEffective(service)

    fun onRulesChanged() = startQueryJob()

    fun onA11yConnected() {
        if (
            SettingsRepository.settings.value.enableBlockA11yAppList &&
                !SettingsRepository.actualBlockA11yAppList.contains(
                    topAppIdFlow.value,
                )
        ) {
            startQueryJob(byForced = true)
        } else {
            coordinator.diagnostics.entry("service_connected", "connection_query_not_required")
        }
    }

    fun onScreenForcedActive() {
        // 关闭屏幕 -> Activity::onStop -> 点亮屏幕 -> Activity::onStart -> Activity::onResume
        AndroidForegroundState.onScreenForcedActive()
        startQueryJob()
    }

    val safeActiveWindow: AccessibilityNodeInfo?
        get() =
            readA11yWindow(coordinator.diagnostics, "ActiveWindow") {
                service.windowNodeInfo?.setGeneratedTime()
            }

    private val scope
        get() = service.scope

    @Volatile private var latestStateEvent: A11yEvent? = null
    private var lastEventTime = 0L
    private val eventDeque =
        MatchEventQueue<A11yEvent>(64, { it.key }, { it.close() }) { removed, capacity ->
            val trace = coordinator.diagnostics
            if (trace.enabled) {
                trace.entry(
                    "event_queue",
                    "event_queue_overflow",
                    eventDrop =
                        MatchEventDrop(
                            removed.key,
                            capacity,
                            monotonicTimeMillis() - removed.time,
                        ),
                )
            }
        }
    private var eventConsumer: Job? = null

    fun onA11yEvent(event: AccessibilityEvent?) {
        // Own content changes are ignored by matching; omit their diagnostic noise as well.
        if (event?.eventType == CONTENT_CHANGED && event.packageName == META.appId) return
        val diagnosticEventId =
            if (event != null && coordinator.diagnostics.enabled)
                diagnosticEventIds.incrementAndGet()
            else null
        if (event != null && diagnosticEventId != null) {
            coordinator.diagnostics.record(
                0,
                MatchStage.Root,
                "accessibility_event_received",
                windowEvent =
                    MatchWindowEvent(
                        event.eventType,
                        event.windowId,
                        event.contentChangeTypes,
                        if (AndroidTarget.P) event.windowChanges else null,
                        event.eventTime,
                        event.packageName?.toString(),
                        event.className?.toString(),
                        eventId = diagnosticEventId,
                        text = event.text.take(8).map { it.diagnosticText().orEmpty() },
                        contentDescription = event.contentDescription.diagnosticText(),
                    ),
            )
        }
        if (!effective) {
            coordinator.diagnostics.entry("event", "service_not_effective")
            return
        }
        if (!event.isUseful()) {
            coordinator.diagnostics.entry("event", "event_not_useful")
            return
        }
        val key =
            MatchEventKey(
                if (event.eventType == CONTENT_CHANGED) MatchEventKind.Content
                else MatchEventKind.WindowState,
                event.packageName.toString(),
                event.className.toString(),
            )
        val now = monotonicTimeMillis()
        val primaryDisplay = !AndroidTarget.TIRAMISU || event.displayId == Display.DEFAULT_DISPLAY
        if (primaryDisplay) {
            if (event.eventType == STATE_CHANGED) captureScreenshotOnEvent(event)
            if (event.packageName == launcherAppId) refreshSubscriptionsOnLauncher()
        }
        val accepted =
            MatchingRuntime.inputs.acceptAccessibility(
                key,
                isInteractive,
                META.appId,
                MainActivityVisibility.isVisible,
                imeAppId,
                currentTopActivity.appId,
                now - appChangeTime,
                now - lastTriggerTime,
                primaryDisplay,
                event.recordCount,
                event.action,
                event.isFullScreen,
                timing,
                event.eventTime,
            )
        if (!accepted) return
        val a11yEvent =
            event.toA11yEvent(key, diagnosticEventId)
                ?: run {
                    coordinator.diagnostics.entry("event", "event_metadata_unavailable")
                    return
                }
        EventService.logEvent(event)
        if (META.debuggable) {
            Log.d(
                "onNewA11yEvent",
                "type:${event.eventType}, time:${event.eventTime - lastEventTime}, app:${event.packageName}, cls:${event.className}",
            )
        }
        lastEventTime = event.eventTime
        if (event.eventType == STATE_CHANGED) {
            latestStateEvent = a11yEvent
            AndroidForegroundState.withTopActivityLock {
                val foreground = AndroidForegroundState.foregroundSnapshot()
                val appId = a11yEvent.key.appId
                val identity =
                    if (isActivity(appId, a11yEvent.key.name)) {
                        ForegroundIdentity(appId, a11yEvent.key.name)
                    } else {
                        windows.knownIdentity(event.windowId, appId)
                            ?: foreground.pending?.takeIf { it.appId == appId }
                            ?: foreground.confirmed.takeIf { it.appId == appId }
                            ?: ForegroundIdentity(appId)
                    }
                if (windows.observeState(event.windowId, identity)) {
                    AndroidForegroundState.onWindowChanged()
                }
            }
            // Window metadata is available without fetching source. Both service modes
            // must record visits even while their accessibility Binder reads are stalled.
            if (isActivity(a11yEvent.key.appId, a11yEvent.key.name)) {
                updateTopActivity(
                    a11yEvent.key.appId,
                    a11yEvent.key.name,
                    sourceTimeMillis = a11yEvent.sourceTimeMillis,
                )
            }
        }
        synchronized(eventDeque) {
            if (scope.coroutineContext[Job]?.isActive != true) {
                a11yEvent.close()
                coordinator.diagnostics.entry("event", "service_scope_closed")
                return
            }
            // Bound pending platform references even when a Binder read stalls.
            eventDeque.offer(a11yEvent)
            consumePendingEvents()
        }
    }

    private fun consumePendingEvents() {
        if (eventConsumer != null) return
        val job =
            scope.launch(eventDispatcher, start = CoroutineStart.LAZY) {
                while (true) {
                    val head = synchronized(eventDeque) { eventDeque.firstOrNull() } ?: break
                    consumeEvent(head)
                }
            }
        eventConsumer = job
        job.invokeOnCompletion {
            synchronized(eventDeque) {
                eventConsumer = null
                if (eventDeque.isNotEmpty() && scope.coroutineContext[Job]?.isActive == true)
                    consumePendingEvents()
            }
        }
        job.start()
    }

    private data class PageNode(
        val page: Any,
        val node: AccessibilityNodeInfo,
        val provenance: MatchNodeProvenance? = null,
    )

    private fun observedPageNode(
        page: Any,
        node: AccessibilityNodeInfo,
        source: MatchNodeSource,
        reason: MatchNodeReadReason,
        parentReadId: Long? = null,
        eventId: Long? = null,
    ): PageNode {
        val provenance =
            if (coordinator.diagnostics.enabled)
                MatchNodeProvenance(
                    readId = diagnosticReadIds.incrementAndGet(),
                    connectionId = connectionId,
                    service = service.mode,
                    source = source,
                    reason = reason,
                    page = System.identityHashCode(page),
                    appId = node.packageName?.toString().orEmpty(),
                    observedActivityId = currentTopActivity.activityId,
                    windowId = node.windowId,
                    acquiredAtMillis = monotonicTimeMillis(),
                    parentReadId = parentReadId,
                    eventId = eventId,
                    summary =
                        Rect().let { bounds ->
                            node.getBoundsInScreen(bounds)
                            MatchNodeSummary(
                                node.className?.toString(),
                                node.viewIdResourceName,
                                node.text.diagnosticText(),
                                node.contentDescription.diagnosticText(),
                                bounds.left,
                                bounds.top,
                                bounds.right,
                                bounds.bottom,
                                node.childCount,
                            )
                        },
                )
            else null
        if (provenance != null)
            coordinator.diagnostics.record(
                0,
                MatchStage.Root,
                "node_acquired",
                node = provenance,
            )
        return PageNode(page, node, provenance)
    }

    private fun queryRootNode(): AccessibilityNodeInfo? {
        val root = AndroidForegroundState.withTopActivityLock {
            availablePageRoot()?.takeIf {
                a11yContext.queryPage == null || it.page === a11yContext.queryPage
            }
        }
        queryRoot = root
        return root?.node
    }

    private fun observeQueryNode(node: AccessibilityNodeInfo, role: MatchNodeRole) {
        if (!coordinator.diagnostics.enabled) return
        val origin =
            when {
                querySource?.node === node -> querySource?.provenance
                queryRoot?.node === node -> queryRoot?.provenance
                else -> null
            }
        coordinator.diagnostics.record(
            a11yContext.queryRunId,
            MatchStage.Root,
            "node_used",
            rule = a11yContext.currentRule?.matchIdentity(),
            node = origin?.copy(ageMillis = monotonicTimeMillis() - origin.acquiredAtMillis),
            nodeRole = role,
        )
    }

    private val sourceLock = Any()
    private var pendingSource: PageNode? = null
    @Volatile private var rootNode: PageNode? = null

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion {
            eventDeque.clear()
            synchronized(sourceLock) { pendingSource = null }
            rootNode = null
        }
    }

    private suspend fun consumeEvent(headEvent: A11yEvent) {
        val consumedEvents = eventDeque.takeBatch(headEvent)
        if (consumedEvents.isEmpty()) return
        try {
            val latestEvent = consumedEvents.last()
            if (coordinator.diagnostics.enabled) {
                coordinator.diagnostics.record(
                    0,
                    MatchStage.Root,
                    "event_batch_selected",
                    eventBatch =
                        MatchEventBatch(
                            consumedEvents.mapNotNull { it.diagnosticEventId },
                            latestEvent.diagnosticEventId,
                        ),
                )
            }
            val stateEvent = latestStateEvent
            if (stateEvent != null && latestEvent.sourceTimeMillis < stateEvent.sourceTimeMillis) {
                coordinator.diagnostics.entry("consume_event", "stale_event_before_source")
                return
            }
            val page = AndroidForegroundState.currentPage
            // Root and source Binder reads run independently, outside the matching lane.
            requestRoot(MatchNodeReadReason.Event)
            coordinator.diagnostics.entry("source_read", "source_read_started")
            val node =
                try {
                    readNode("Source", page) { latestEvent.takeSource() }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IllegalStateException) {
                    // A cleared event may no longer be sealed; the read operation retains its
                    // cause.
                    null
                }
                    ?: run {
                        coordinator.diagnostics.entry("source_read", "source_null")
                        return
                    }
            coordinator.diagnostics.entry("source_read", "source_ready")
            if (
                !effective ||
                    page !== AndroidForegroundState.currentPage ||
                    stateEvent !== latestStateEvent ||
                    (stateEvent != null &&
                        latestEvent.sourceTimeMillis < stateEvent.sourceTimeMillis) ||
                    node.packageName?.toString() != latestEvent.key.appId
            ) {
                coordinator.diagnostics.entry("consume_event", "stale_or_mismatched_source")
                return
            }
            val accepted = AndroidForegroundState.withTopActivityLock {
                if (
                    !effective ||
                        page !== AndroidForegroundState.currentPage ||
                        stateEvent !== latestStateEvent
                )
                    return@withTopActivityLock false
                val foreground = AndroidForegroundState.foregroundSnapshot()
                if (
                    latestEvent.key.appId != foreground.confirmed.appId &&
                        latestEvent.key.appId != foreground.pending?.appId
                ) {
                    coordinator.diagnostics.entry("consume_event", "source_foreground_mismatch")
                    return@withTopActivityLock false
                }
                // A delayed source from another window must not confirm the active page.
                if (!windows.isCurrentKnownWindow(node.windowId, latestEvent.key.appId)) {
                    coordinator.diagnostics.entry("consume_event", "source_window_mismatch")
                    return@withTopActivityLock false
                }
                updateTopActivity(
                    latestEvent.key.appId,
                    windows.identity?.activityId,
                    ActivityScene.Nodes,
                )
                if (
                    AndroidForegroundState.foregroundPending ||
                        currentTopActivity.appId != latestEvent.key.appId
                )
                    return@withTopActivityLock false
                val activityRule = foregroundRulesFlow.value
                if (activityRule.skipConsumeEvent || !SettingsRepository.settings.value.enableMatch)
                    return@withTopActivityLock false
                synchronized(sourceLock) {
                    pendingSource =
                        observedPageNode(
                            AndroidForegroundState.currentPage,
                            node,
                            MatchNodeSource.Event,
                            MatchNodeReadReason.Event,
                            eventId = latestEvent.diagnosticEventId,
                        )
                }
                true
            }
            if (!accepted) return
            a11yContext.interruptKey++
            startQueryJob(byEvent = latestEvent)
        } catch (error: MatchNodeReadUnavailable) {
            coordinator.diagnostics.entry("Source", checkNotNull(error.message))
        } finally {
            consumedEvents.forEach { it.close() }
        }
    }

    private fun availablePageRoot(): PageNode? = rootNode?.takeIf {
        it.page === AndroidForegroundState.currentPage &&
            !it.node.isExpired(1000) &&
            windows.isCurrentKnownWindow(it.node.windowId, it.node.packageName?.toString())
    }

    private var rootRequest: Job? = null
    private var rootRequestPage: Any? = null

    private fun requestRoot(reason: MatchNodeReadReason) {
        val page = AndroidForegroundState.currentPage
        val job =
            synchronized(this) {
                if (!effective) return
                if (rootRequest?.isCompleted == false && rootRequestPage === page) return
                rootRequest?.cancel()
                rootRequestPage = page
                scope
                    .launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                        coordinator.diagnostics.entry("root_read", "root_read_started")
                        val node =
                            try {
                                readNode("Root", page) { safeActiveWindow }
                            } catch (error: MatchNodeReadUnavailable) {
                                coordinator.diagnostics.entry("Root", checkNotNull(error.message))
                                return@launch
                            }
                        if (node == null) {
                            coordinator.diagnostics.entry("root_read", "root_unavailable")
                            return@launch
                        }
                        val accepted = AndroidForegroundState.withTopActivityLock {
                            if (!effective || page !== AndroidForegroundState.currentPage)
                                return@withTopActivityLock false
                            val appId =
                                node.packageName?.toString() ?: return@withTopActivityLock false
                            val foreground = AndroidForegroundState.foregroundSnapshot()
                            if (windows.observeRoot(node.windowId, appId, foreground.confirmed)) {
                                AndroidForegroundState.onWindowChanged()
                            }
                            val identity = windows.identity
                            if (identity == null) {
                                coordinator.diagnostics.entry(
                                    "root_read",
                                    "window_identity_pending",
                                )
                                return@withTopActivityLock false
                            }
                            updateTopActivity(appId, identity.activityId, ActivityScene.Nodes)
                            if (
                                AndroidForegroundState.foregroundPending ||
                                    currentTopActivity.appId != appId
                            )
                                return@withTopActivityLock false
                            rootNode =
                                observedPageNode(
                                    AndroidForegroundState.currentPage,
                                    node,
                                    MatchNodeSource.Root,
                                    reason,
                                )
                            true
                        }
                        if (accepted) {
                            coordinator.diagnostics.entry("root_read", "root_ready")
                            startQueryJob()
                        } else {
                            coordinator.diagnostics.entry("root_read", "stale_or_mismatched_root")
                        }
                    }
                    .also { rootRequest = it }
            }
        job.start()
    }

    private suspend fun readNode(
        name: String,
        page: Any,
        read: () -> AccessibilityNodeInfo?,
    ): AccessibilityNodeInfo? {
        val reader = MatchingRuntime.nodeReader
        val timeout = timing.rootWaitMillis
        return reader.query {
            reader.read(timeout, name = name, page = page, block = read)
        }
    }

    private var foregroundCheck: Job? = null

    @Synchronized
    fun confirmForeground() {
        if (foregroundCheck?.isActive == true) return
        foregroundCheck =
            scope.launch(Dispatchers.IO) {
                val policy = timing
                val deadline = monotonicTimeMillis() + policy.foregroundWaitMillis
                do {
                    requestRoot(MatchNodeReadReason.ForegroundConfirmation)
                    if (!AndroidForegroundState.foregroundPending) {
                        startQueryJob()
                        return@launch
                    }
                    delay(policy.foregroundRetryMillis)
                } while (effective && monotonicTimeMillis() < deadline)
                coordinator.diagnostics.entry(
                    "foreground_confirmation",
                    if (effective) "foreground_wait_expired" else "service_not_effective",
                )
            }
    }

    private fun startQueryJob(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
    ) {
        if (queryBlocked("query_request")) return
        if (AndroidForegroundState.foregroundPending) {
            coordinator.diagnostics.entry("query_request", "foreground_pending")
            return confirmForeground()
        }
        val activityRule = foregroundRulesFlow.value
        if (activityRule.currentRules.isEmpty()) {
            coordinator.diagnostics.entry("query_request", activityRule.emptyReason)
            return
        }
        coordinator.request(service) {
            try {
                queryAction(this, byEvent, byForced)
            } finally {
                checkFutureStartJob()
            }
        }
    }

    private fun queryBlocked(source: String, runId: Long = 0): Boolean {
        val reason =
            when {
                !effective -> "service_not_effective"
                !isInteractive -> "screen_not_interactive"
                !SettingsRepository.settings.value.enableMatch -> "matching_disabled"
                else -> return false
            }
        coordinator.diagnostics.entry(source, reason, runId)
        return true
    }

    private fun checkFutureStartJob() {
        if (queryBlocked("follow_up")) return
        val t = monotonicTimeMillis()
        val recent =
            t - lastTriggerTime < timing.followUpWindowMillis ||
                t - appChangeTime < timing.followUpWindowMillis
        if (recent || foregroundRulesFlow.value.hasFeatureAction) {
            coordinator.scheduleFollowUp(service) {
                try {
                    queryAction(this, byForced = !recent)
                } finally {
                    checkFutureStartJob()
                }
            }
        } else {
            coordinator.diagnostics.entry("follow_up", "follow_up_window_expired")
        }
    }

    private fun fixAppId(rightAppId: String) {
        if (currentTopActivity.appId == rightAppId) return
        val page = AndroidForegroundState.currentPage
        val context = privilegeContextFlow.value
        val topCpn = context?.topCpn()
        AndroidForegroundState.withTopActivityLock {
            if (
                page !== AndroidForegroundState.currentPage ||
                    context !== privilegeContextFlow.value
            )
                return@withTopActivityLock
            if (topCpn?.packageName == rightAppId) {
                updateTopActivity(topCpn.packageName, topCpn.className, ActivityScene.Nodes)
            } else {
                updateTopActivity(rightAppId, null, ActivityScene.Nodes)
            }
        }
        coordinator.scheduleFollowUp(service) {
            try {
                queryAction(this)
            } finally {
                checkFutureStartJob()
            }
        }
    }

    private suspend fun queryAction(
        work: MatchWork,
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
    ) {
        work.ensureCurrent()
        if (queryBlocked("query_start", work.id)) return
        if (AndroidForegroundState.foregroundPending) {
            coordinator.diagnostics.entry("query_start", "foreground_pending", work.id)
            confirmForeground()
            return
        }
        if (!firstQueryStarted) {
            firstQueryStarted = true
            AndroidAutomationSessions.recordServiceEvent(service, MatchServiceEvent.QueryStarted)
        }
        val activityRule = AndroidForegroundState.currentRule
        val page = AndroidForegroundState.currentPage
        work.trackRules(activityRule)
        querySource =
            synchronized(sourceLock) {
                pendingSource.also { pendingSource = null }?.takeIf { it.page === page }
            }
        val lastNode = querySource?.node

        if (!a11yContext.clearOldAppNodeCache()) {
            if (byEvent != null || lastNode != null) {
                // 新事件到来时, 若缓存清理不及时会导致无法查询到节点
                a11yContext.clearNodeCache(lastNode)
            }
        }
        // Only the serial matcher updates selector caches. A page transition must
        // not leave a previous page's root reachable through parent/root selectors.
        a11yContext.rootCache.value = queryRootNode()
        a11yContext.queryJob = currentCoroutineContext()[Job]
        a11yContext.queryRunId = work.id
        a11yContext.queryPage = page
        try {
            queryRules(
                work,
                activityRule,
                queryNodes,
                source = lastNode,
                forced = byForced,
                isCurrent = {
                    AndroidForegroundState.withTopActivityLock {
                        MatchExecutionState(
                                page = AndroidForegroundState.currentPage,
                                serviceEffective = effective,
                                interactive = isInteractive,
                                foregroundPending = AndroidForegroundState.foregroundPending,
                                windowKnown = windows.identity != null,
                                matchingEnabled = SettingsRepository.settings.value.enableMatch,
                            )
                            .permits(page)
                    }
                },
                schedule = { rule, kind, duration ->
                    coordinator.scheduleRule(service, rule, kind, duration) {
                        try {
                            queryAction(this)
                        } finally {
                            checkFutureStartJob()
                        }
                    }
                },
                correctApp = { appId -> scope.launch(eventDispatcher) { fixAppId(appId) } },
                completed = { rule, target, result ->
                    AndroidForegroundState.withTopActivityLock {
                        if (
                            page === AndroidForegroundState.currentPage &&
                                !AndroidForegroundState.foregroundPending &&
                                coordinator.canCommit(work.id, rule)
                        ) {
                            rule.trigger()
                            SettingsRepository.incrementActionCount()
                            if (result.action != ActionPerformer.None.action)
                                AndroidToastUtils.showAction(rule, work.id)
                        }
                    }
                    addActionLog(rule, activityRule.topActivity, target, result)
                },
            )
        } finally {
            a11yContext.queryJob = null
            a11yContext.queryPage = null
            querySource = null
            queryRoot = null
        }
    }
}
