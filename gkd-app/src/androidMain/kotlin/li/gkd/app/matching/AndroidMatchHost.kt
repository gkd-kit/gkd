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
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.matching.action.ActionPerformer
import li.gkd.app.matching.diagnostics.MatchEventBatch
import li.gkd.app.matching.diagnostics.MatchEventDrop
import li.gkd.app.matching.diagnostics.MatchNodeProvenance
import li.gkd.app.matching.diagnostics.MatchNodeReadReason
import li.gkd.app.matching.diagnostics.MatchNodeRole
import li.gkd.app.matching.diagnostics.MatchNodeSource
import li.gkd.app.matching.diagnostics.MatchNodeSummary
import li.gkd.app.matching.diagnostics.MatchRejectedWindow
import li.gkd.app.matching.diagnostics.MatchServiceEvent
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.MatchWindowEvent
import li.gkd.app.matching.diagnostics.diagnosticText
import li.gkd.app.matching.input.A11yEvent
import li.gkd.app.matching.input.ActivityScene
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.input.CONTENT_CHANGED
import li.gkd.app.matching.input.ForegroundDecisionReason
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
import li.gkd.app.matching.input.topTaskAppId
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
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.snapshot.captureScreenshotOnEvent
import li.gkd.app.subscription.refreshSubscriptionsOnLauncher
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AndroidToastUtils
import li.gkd.app.util.Constants
import li.gkd.app.util.monotonicTimeMillis

private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val diagnosticConnectionIds = AtomicLong()
private val diagnosticReadIds = AtomicLong()
private val diagnosticEventIds = AtomicLong()

private sealed interface ActiveWindowRead {
    data class Node(val node: AccessibilityNodeInfo) : ActiveWindowRead

    data class BlockedSystemUi(val windowId: Int) : ActiveWindowRead
}

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
                    topTaskAppId,
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

    fun readActiveWindow(
        onUnavailable: ((RuntimeException?) -> Unit)? = null,
    ): AccessibilityNodeInfo? =
        readA11yWindow(coordinator.diagnostics, "ActiveWindow", onUnavailable) {
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
                if (event.eventType == STATE_CHANGED) "accessibility_window_state_received"
                else "accessibility_event_received",
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
                    windowId = a11yEvent.windowId,
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
            val page = AndroidForegroundState.withTopActivityLock {
                val currentPage = AndroidForegroundState.currentPage
                // Skip root and source reads for content changes in a confirmed window
                // when matching is disabled or no rules apply. Window transitions and
                // foreground conflicts still need node evidence.
                if (
                    latestEvent.key.kind == MatchEventKind.Content &&
                        !AndroidForegroundState.foregroundPending &&
                        rootNode?.page === currentPage &&
                        windows.isCurrentKnownWindow(latestEvent.windowId, latestEvent.key.appId) &&
                        (!SettingsRepository.settings.value.enableMatch ||
                            foregroundRulesFlow.value.currentRules.isEmpty())
                ) {
                    null
                } else {
                    currentPage
                }
            }
            if (page == null) {
                coordinator.diagnostics.entry("consume_event", "confirmed_content_without_matching")
                return
            }
            // Root and source Binder reads run independently, outside the matching lane.
            val appId = latestEvent.key.appId
            requestRoot(MatchNodeReadReason.Event, latestEvent)
            if (SettingsRepository.checkAppBlockMatch(appId)) {
                coordinator.diagnostics.entry("source_read", "source_app_blocked")
                return
            }
            val rules = SubscriptionState.ruleSummaryFlow.value.value
            if (
                rules != null &&
                    rules.appIdToRules[appId].isNullOrEmpty() &&
                    rules.globalRules.none { it.matchActivity(appId, activityId = null) }
            ) {
                coordinator.diagnostics.entry("source_read", "no_rules_for_app")
                return
            }
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
                recordRejectedWindow(
                    "consume_event",
                    "stale_or_mismatched_source",
                    node,
                    latestEvent,
                )
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
                    recordRejectedWindow(
                        "consume_event",
                        "source_foreground_mismatch",
                        node,
                        latestEvent,
                    )
                    return@withTopActivityLock false
                }
                // A delayed source from another window must not confirm the active page.
                val windowId = node.windowId
                if (!windows.isCurrentKnownWindow(windowId, latestEvent.key.appId)) {
                    recordRejectedWindow(
                        "consume_event",
                        "source_window_mismatch",
                        node,
                        latestEvent,
                    )
                    return@withTopActivityLock false
                }
                updateTopActivity(
                    latestEvent.key.appId,
                    windows.identity?.activityId,
                    ActivityScene.Nodes,
                    windowId = windowId,
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

    private fun recordRejectedWindow(
        source: String,
        reason: String,
        node: AccessibilityNodeInfo,
        event: A11yEvent? = null,
    ) {
        if (!coordinator.diagnostics.enabled) return
        coordinator.diagnostics.entry(
            source,
            reason,
            rejectedWindow =
                MatchRejectedWindow(
                    node.packageName?.toString(),
                    node.windowId,
                    event?.diagnosticEventId,
                ),
        )
    }

    private var rootRequest: Job? = null
    private var rootRequestPage: Any? = null

    private fun readActiveWindowRoot(): ActiveWindowRead.Node? =
        service.windowNodeInfo?.setGeneratedTime()?.let { ActiveWindowRead.Node(it) }

    private fun readRootAroundBlockedSystemUi(eventWindowId: Int?): ActiveWindowRead? {
        val window = service.windowInfos.firstOrNull { it.isActive }
        if (window != null) {
            val windowId = window.id
            if (windowId == eventWindowId) return ActiveWindowRead.BlockedSystemUi(windowId)
            val appId =
                AndroidForegroundState.withTopActivityLock { windows.knownAppId(windowId) }
                    ?: privilegeContextFlow.value?.accessibilityWindowPackage(windowId)
            if (appId == Constants.systemUiAppId) return ActiveWindowRead.BlockedSystemUi(windowId)
            if (appId != null) {
                window.root?.let {
                    return ActiveWindowRead.Node(it.setGeneratedTime())
                }
            }
        }
        if (AndroidForegroundState.foregroundSnapshot().surfaceCovered == true) return null
        return readActiveWindowRoot()
    }

    private fun confirmBlockedSystemUiWindow(page: Any, windowId: Int) {
        AndroidForegroundState.withTopActivityLock {
            val appId = Constants.systemUiAppId
            if (
                !effective ||
                    page !== AndroidForegroundState.currentPage ||
                    appId !in SettingsRepository.blockMatchAppList.value
            )
                return@withTopActivityLock
            val foreground = AndroidForegroundState.foregroundSnapshot()
            if (foreground.pending?.appId?.let { it != appId } == true) return@withTopActivityLock
            val identity = windows.knownIdentity(windowId, appId) ?: ForegroundIdentity(appId)
            if (windows.observeState(windowId, identity)) AndroidForegroundState.onWindowChanged()
            updateTopActivity(
                appId,
                identity.activityId,
                ActivityScene.ActiveWindow,
                windowId = windowId,
            )
        }
    }

    private fun requestRoot(reason: MatchNodeReadReason, event: A11yEvent? = null) {
        val job = AndroidForegroundState.withTopActivityLock {
            if (!effective) return@withTopActivityLock null
            val page = AndroidForegroundState.currentPage
            if (rootRequest?.isCompleted == false && rootRequestPage === page)
                return@withTopActivityLock null
            val systemUi = Constants.systemUiAppId
            val systemUiBlocked = systemUi in SettingsRepository.blockMatchAppList.value
            val eventAppId = event?.key?.appId
            val checkCoveredTask =
                event?.key?.kind == MatchEventKind.Content &&
                    eventAppId != systemUi &&
                    privilegeContextFlow.value != null
            val foreground =
                if (
                    systemUiBlocked &&
                        (checkCoveredTask || reason == MatchNodeReadReason.ForegroundConfirmation)
                )
                    AndroidForegroundState.foregroundSnapshot()
                else null
            if (
                checkCoveredTask &&
                    foreground?.surfaceCovered == true &&
                    foreground.confirmed.appId == systemUi &&
                    foreground.pending == null &&
                    foreground.task?.appId == eventAppId
            ) {
                coordinator.diagnostics.entry("root_read", "root_skipped_under_system_ui")
                return@withTopActivityLock null
            }
            val blockedSystemUiWindowId =
                if (systemUiBlocked && eventAppId == systemUi) event.windowId else null
            val confirmSystemUiWindow =
                if (reason == MatchNodeReadReason.ForegroundConfirmation && foreground != null) {
                    if (
                        foreground.confirmed.appId == systemUi &&
                            foreground.reason == ForegroundDecisionReason.ActiveWindowConfirmed
                    )
                        return@withTopActivityLock null
                    foreground.pending?.appId == systemUi
                } else false
            rootRequest?.cancel()
            rootRequestPage = page
            scope
                .launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                    coordinator.diagnostics.entry("root_read", "root_read_started")
                    val readRoot = {
                        if (blockedSystemUiWindowId != null || confirmSystemUiWindow)
                            readRootAroundBlockedSystemUi(blockedSystemUiWindowId)
                        else readActiveWindowRoot()
                    }
                    val result =
                        try {
                            if (!coordinator.diagnostics.enabled) {
                                readNode("Root", page) {
                                    readA11yWindow(
                                        coordinator.diagnostics,
                                        "ActiveWindow",
                                        read = readRoot,
                                    )
                                }
                            } else {
                                // Keep nested diagnostic publication outside the timed worker.
                                readA11yWindow(coordinator.diagnostics, "ActiveWindow") {
                                    readNode("Root", page, readRoot)
                                }
                            }
                        } catch (error: MatchNodeReadUnavailable) {
                            coordinator.diagnostics.entry("Root", checkNotNull(error.message))
                            return@launch
                        }
                    val node =
                        when (result) {
                            is ActiveWindowRead.Node -> result.node
                            is ActiveWindowRead.BlockedSystemUi -> {
                                confirmBlockedSystemUiWindow(page, result.windowId)
                                return@launch
                            }
                            null -> {
                                coordinator.diagnostics.entry("root_read", "root_unavailable")
                                return@launch
                            }
                        }
                    val accepted = AndroidForegroundState.withTopActivityLock {
                        if (!effective || page !== AndroidForegroundState.currentPage)
                            return@withTopActivityLock false
                        val appId = node.packageName?.toString() ?: return@withTopActivityLock false
                        val windowId = node.windowId
                        val foreground = AndroidForegroundState.foregroundSnapshot()
                        if (windows.observeRoot(windowId, appId, foreground.confirmed)) {
                            AndroidForegroundState.onWindowChanged()
                        }
                        val identity = windows.identity
                        if (identity == null) {
                            recordRejectedWindow("root_read", "window_identity_pending", node)
                            return@withTopActivityLock false
                        }
                        updateTopActivity(
                            appId,
                            identity.activityId,
                            ActivityScene.Nodes,
                            windowId = windowId,
                            // Only a fresh active root can disprove a launcher window event.
                            activeRootLauncherAppId =
                                launcherAppId.takeIf {
                                    it == foreground.pending?.appId &&
                                        privilegeContextFlow.value != null
                                },
                        )
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
                        recordRejectedWindow("root_read", "stale_or_mismatched_root", node)
                    }
                }
                .also { rootRequest = it }
        }
        job?.start()
    }

    private suspend fun <T> readNode(
        name: String,
        page: Any,
        read: () -> T?,
    ): T? {
        val reader = MatchingRuntime.nodeReader
        val timeout = timing.rootWaitMillis
        return reader.query {
            reader.read(timeout, name = name, page = page, block = read)
        }
    }

    private var foregroundCheck: Job? = null

    @Synchronized
    fun confirmForeground(restart: Boolean = false) {
        if (foregroundCheck?.isActive == true) {
            if (!restart) return
            foregroundCheck?.cancel()
        }
        foregroundCheck =
            scope.launch(Dispatchers.IO) {
                val policy = timing
                val deadline = monotonicTimeMillis() + policy.foregroundWaitMillis
                do {
                    requestRoot(MatchNodeReadReason.ForegroundConfirmation)
                    if (!AndroidForegroundState.foregroundPending) break
                    delay(policy.foregroundRetryMillis)
                } while (
                    AndroidForegroundState.foregroundPending &&
                        effective &&
                        monotonicTimeMillis() < deadline
                )
                if (!AndroidForegroundState.foregroundPending) {
                    startQueryJob()
                    return@launch
                }
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
