package li.gkd.app.a11y

import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.META
import li.gkd.app.data.ActionPerformer
import li.gkd.app.data.AppRule
import li.gkd.app.data.ResolvedRule
import li.gkd.app.data.RuleStatus
import li.gkd.app.platform.lifecycle.MainActivityVisibility
import li.gkd.app.service.EventService
import li.gkd.app.service.topAppIdFlow
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.store.AppStore.actualBlockA11yAppList
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.launchLogged
import li.gkd.app.util.ToastUtils.showActionToast
import li.gkd.app.util.systemUiAppId
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds


private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val actionDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

class A11yRuleEngine(private val service: A11yCommonImpl) {
    private val a11yContext = A11yContext(getRoot = { safeActiveWindow })
    private val effective get() = A11yRuntime.isEffective(service)
    private val hasOthersService = A11yRuntime.hasOtherService(service)

    fun onA11yConnected() {
        if (storeFlow.value.enableBlockA11yAppList && !actualBlockA11yAppList.contains(topAppIdFlow.value)) {
            startQueryJob(byForced = true)
        }
    }

    fun onScreenForcedActive() {
        // Screen off -> Activity::onStop -> Screen on -> Activity::onStart -> Activity::onResume
        A11yState.onScreenForcedActive()
        startQueryJob()
    }

    val safeActiveWindow: AccessibilityNodeInfo?
        get() = try {
            // Some apps take 554ms
            // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
            service.windowNodeInfo?.setGeneratedTime()
        } catch (_: Throwable) {
            null
        }.apply {
            a11yContext.rootCache.value = this
        }

    private val safeActiveWindowAppId: String?
        get() = safeActiveWindow?.packageName?.toString()

    private val scope get() = service.scope

    @Volatile
    private var latestStateEvent: A11yEvent? = null
    private var lastContentEventTime = 0L
    private var lastEventTime = 0L
    private val eventDeque = ArrayDeque<A11yEvent>()
    fun onA11yEvent(event: AccessibilityEvent?) {
        if (!effective) return
        if (!event.isUseful()) return
        // Reject secondary screen accessibility events
        if (AndroidTarget.TIRAMISU && event.displayId != Display.DEFAULT_DISPLAY) return
        onA11yFeatEvent(event)
        if (event.eventType == CONTENT_CHANGED) {
            if (!isInteractive) return // Accessibility event type:2048 still exists after screen off, time:8094, app:com.miui.aod, cls:android.widget.TextView
            if (event.packageName == systemUiAppId && event.packageName != currentTopActivity.appId) return
        }
        // Filter partial input method events
        if (event.packageName == imeAppId && currentTopActivity.appId != imeAppId) {
            if (event.recordCount == 0 && event.action == 0 && !event.isFullScreen) return
        }
        // Discard its own events directly and update topActivity independently
        if (
            (event.eventType == CONTENT_CHANGED || !MainActivityVisibility.isVisible) &&
            event.packageName == META.appId
        ) return

        val a11yEvent = event.toA11yEvent() ?: return
        if (a11yEvent.type == CONTENT_CHANGED) {
            // Prevent content type events from being too fast
            if (a11yEvent.time - lastContentEventTime < 100 && a11yEvent.time - appChangeTime > 5000 && a11yEvent.time - lastTriggerTime > 3000) {
                return
            }
            lastContentEventTime = a11yEvent.time
        }
        EventService.logEvent(event)
        if (META.debuggable) {
            Log.d(
                "onNewA11yEvent",
                "type:${event.eventType}, time:${event.eventTime - lastEventTime}, app:${event.packageName}, cls:${event.className}"
            )
        }
        if (event.eventTime < lastEventTime) {
            // Some apps send negative time events; discard directly
            // type:32, time:-104, app:com.miui.home, cls:com.miui.home.launcher.Launcher
            return
        }
        lastEventTime = event.eventTime
        if (event.eventType == STATE_CHANGED) {
            latestStateEvent = a11yEvent
        }
        synchronized(eventDeque) { eventDeque.addLast(a11yEvent) }
        scope.launch(eventDispatcher) { consumeEvent(a11yEvent) }
    }

    private val queryEvents = mutableListOf<A11yEvent>()
    private suspend fun consumeEvent(headEvent: A11yEvent) {
        val consumedEvents = synchronized(eventDeque) {
            if (eventDeque.firstOrNull() !== headEvent) return
            eventDeque.filter { it.sameAs(headEvent) }.apply {
                repeat(size) { eventDeque.removeFirst() }
            }
        }
        val latestEvent = consumedEvents.last()
        val evAppId = latestEvent.appId
        val evActivityId = latestEvent.name
        val oldAppId = currentTopActivity.appId
        val rightAppId = if (oldAppId == evAppId) {
            evAppId
        } else {
            getTimeoutAppId() ?: return
        }
        if (rightAppId == evAppId) {
            if (latestEvent.type == STATE_CHANGED) {
                A11yState.withTopActivityLock {
                    // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                    if (isActivity(evAppId, evActivityId)) {
                        updateTopActivity(evAppId, evActivityId)
                    }
                }
            }
        }
        if (rightAppId != currentTopActivity.appId) {
            A11yState.withTopActivityLock {
                // In cases such as returning from screen lock or pulling down notification bar, the app does not send events, but system components do.
                val topCpn = privilegeContextFlow.value?.topCpn()
                if (topCpn?.packageName == rightAppId) {
                    updateTopActivity(topCpn.packageName, topCpn.className)
                } else {
                    updateTopActivity(rightAppId, null)
                }
            }
        }
        val activityRule = activityRuleFlow.value
        if (evAppId != rightAppId || activityRule.skipConsumeEvent || !storeFlow.value.enableMatch) {
            return
        }
        synchronized(queryEvents) { queryEvents.addAll(consumedEvents) }
        a11yContext.interruptKey++
        startQueryJob(byEvent = latestEvent)
    }

    private var lastGetAppIdTime = 0L
    private var lastAppId: String? = null
    private suspend fun getTimeoutAppId(): String? {
        if (lastAppId != null && System.currentTimeMillis() - lastGetAppIdTime <= 100) return lastAppId
        // Some apps take a long time to get safeActiveWindow via accessibility, causing multiple events to pile up and block, making it impossible to detect appId switch leading to state anomaly
        // https://github.com/gkd-kit/gkd/issues/622
        lastAppId = withTimeoutOrNull(100.milliseconds) {
            runInterruptible(Dispatchers.IO) { safeActiveWindowAppId }
        } ?: privilegeContextFlow.value?.run { topCpn()?.packageName }
        lastGetAppIdTime = System.currentTimeMillis()
        return lastAppId
    }

    // Some scenarios take 5000 ms
    private suspend fun getTimeoutActiveWindow(): AccessibilityNodeInfo? {
        return suspendCancellableCoroutine { s ->
            val temp = atomic<Continuation<AccessibilityNodeInfo?>?>(s)
            scope.launch(Dispatchers.IO) {
                delay(500L.milliseconds)
                if (s.isActive) {
                    temp.getAndUpdate { null }?.resume(null)
                }
            }
            scope.launch(Dispatchers.IO) {
                val a = safeActiveWindow
                if (s.isActive) {
                    temp.getAndUpdate { null }?.resume(a)
                }
            }
        }
    }

    @Volatile
    private var querying = false

    @Synchronized
    private fun startQueryJob(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        byDelayRule: ResolvedRule? = null,
    ) {
        if (!effective) return
        if (!storeFlow.value.enableMatch) return
        if (activityRuleFlow.value.currentRules.isEmpty()) return
        if (querying) return
        // Getting safeActiveWindow from scratch during accessibility startup is very time-consuming
        if (byEvent == null && service.justStarted && !hasOthersService) return checkFutureStartJob()
        scope.launchLogged(queryDispatcher) {
            querying = true
            val st = if (META.debuggable) System.currentTimeMillis() else 0L
            try {
                if (META.debuggable) {
                    Log.d(
                        "A11yRuleEngine",
                        "startQueryJob start byEvent=${byEvent != null}, byForced=$byForced, byDelayRule=${byDelayRule != null}"
                    )
                }
                queryAction(byEvent, byForced, byDelayRule)
            } finally {
                checkFutureStartJob()
                if (META.debuggable) {
                    val et = System.currentTimeMillis() - st
                    Log.d("A11yRuleEngine", "startQueryJob end $et ms")
                }
                querying = false
            }
        }
    }

    private fun checkFutureStartJob() {
        val t = System.currentTimeMillis()
        if (t - lastTriggerTime < 3000L || t - appChangeTime < 3000L) {
            scope.launch(actionDispatcher) {
                delay(300.milliseconds)
                startQueryJob()
            }
        } else if (activityRuleFlow.value.hasFeatureAction) {
            scope.launch(actionDispatcher) {
                delay(300.milliseconds)
                startQueryJob(byForced = true)
            }
        }
    }

    private fun fixAppId(rightAppId: String) {
        if (currentTopActivity.appId == rightAppId) return
        A11yState.withTopActivityLock {
            val topCpn = privilegeContextFlow.value?.topCpn()
            if (topCpn?.packageName == rightAppId) {
                updateTopActivity(topCpn.packageName, topCpn.className)
            } else {
                updateTopActivity(rightAppId, null)
            }
        }
        scope.launch(actionDispatcher) {
            delay(300.milliseconds)
            startQueryJob()
        }
    }

    private suspend fun queryAction(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        delayRule: ResolvedRule? = null,
    ) {
        val tempStateEvent = latestStateEvent
        val newEvents = if (delayRule != null) {// Deferred rules do not consume events
            null
        } else {
            synchronized(queryEvents) {
                if (byEvent != null && queryEvents.isEmpty()) {
                    return
                }
                (if (queryEvents.size > 1) {
                    val hasDiffItem = queryEvents.any { e ->
                        queryEvents.any { e2 -> !e.sameAs(e2) }
                    }
                    if (hasDiffItem) {
                        // Different event nodes exist; discard all and use root query
                        null
                    } else {
                        // type,appId,className are consistent; need to verify outside synchronized whether it is the same node
                        arrayOf(
                            queryEvents[queryEvents.size - 2],
                            queryEvents.last(),
                        )
                    }
                } else if (queryEvents.size == 1) {
                    arrayOf(queryEvents.last())
                } else {
                    null
                }).apply {
                    queryEvents.clear()
                }
            }
        }
        val activityRule = A11yState.currentRule
        activityRule.currentRules.forEach { rule ->
            if (rule.status == RuleStatus.Status3 && rule.matchDelayJob.value == null) {
                rule.matchDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.matchDelay.milliseconds)
                    rule.matchDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
            }
        }
        if (activityRule.skipMatch) {
            // If the current app has no rules/match pause, do not call to get event nodes to avoid blocking
            return
        }
        var lastNode = if (newEvents == null || newEvents.size <= 1) {
            newEvents?.firstOrNull()?.safeSource
        } else {
            // Get the last two events; if the nodes of the last two events are inconsistent, discard them
            // If equal, it is a continuous event from the same node, commonly seen in countdown interfaces
            val lastNode = newEvents.last().safeSource
            if (lastNode == null || lastNode == newEvents[0].safeSource) {
                lastNode
            } else {
                null
            }
        }
        var lastNodeUsed = false
        if (!a11yContext.clearOldAppNodeCache()) {
            if (byEvent != null) { // This is the majority of cases
                // When new events arrive, if cache is not cleaned up in time, nodes cannot be queried.
                a11yContext.clearNodeCache(lastNode)
            }
        }
        for (rule in activityRule.priorityRules) { // There may be too many rules causing excessive time consumption
            if (!effective) return
            if (checkOutDate(activityRule, tempStateEvent)) break
            if (delayRule != null && delayRule !== rule) continue
            if (rule.status != RuleStatus.StatusOk) continue
            if (byForced && !rule.checkForced()) continue
            lastNode?.let { n ->
                val refreshOk = (!lastNodeUsed) || (try {
                    val e = n.refresh()
                    if (e) {
                        n.setGeneratedTime()
                    }
                    e
                } catch (_: Throwable) {
                    false
                })
                lastNodeUsed = true
                if (!refreshOk) {
                    lastNode = null
                }
            }
            val nodeVal = (lastNode ?: getTimeoutActiveWindow()) ?: continue
            val rightAppId = nodeVal.packageName?.toString() ?: break
            val matchApp = rule.matchActivity(rightAppId)
            if (currentTopActivity.appId != rightAppId || (!matchApp && rule is AppRule)) {
                scope.launch(eventDispatcher) { fixAppId(rightAppId) }
                return
            }
            if (!matchApp) continue
            val target = a11yContext.queryRule(rule, nodeVal) ?: continue
            if (rule.checkDelay() && rule.actionDelayJob.value == null) {
                rule.actionDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.actionDelay.milliseconds)
                    rule.actionDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
                continue
            }
            if (rule.status != RuleStatus.StatusOk) break
            if (checkOutDate(activityRule, tempStateEvent)) break
            val actionResult = rule.performAction(target)
            if (actionResult.result) {
                val topActivity = currentTopActivity
                rule.trigger()
                scope.launch(actionDispatcher) {
                    delay(300.milliseconds)
                    startQueryJob()
                }
                if (actionResult.action != ActionPerformer.None.action) {
                    showActionToast(rule)
                }
                addActionLog(rule, topActivity, target, actionResult)
            }
        }
    }

    private fun checkOutDate(
        activityRule: ActivityRule,
        stateEvent: A11yEvent?
    ): Boolean {
        if (stateEvent !== latestStateEvent) return true
        return activityRule !== A11yState.currentRule
    }

}
