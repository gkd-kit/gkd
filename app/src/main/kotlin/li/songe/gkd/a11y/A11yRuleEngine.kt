package li.songe.gkd.a11y

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.META
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.data.RuleStatus
import li.songe.gkd.isActivityVisible
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.EventService
import li.songe.gkd.service.a11yPartDisabledFlow
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.showActionToast
import li.songe.gkd.util.systemUiAppId
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val actionDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

class A11yRuleEngine(val service: A11yService) {
    init {
        // 关闭屏幕 -> Activity::onStop -> 点亮屏幕 -> Activity::onStart -> Activity::onResume
        service.onScreenForcedActive = {
            val a = topActivityFlow.value
            updateTopActivity(a.appId, a.activityId, scene = ActivityScene.ScreenOn)
            startQueryJob()
        }
        service.onA11yConnected {
            if (storeFlow.value.enableBlockA11yAppList && !a11yPartDisabledFlow.value) {
                startQueryJob(byForced = true)
            }
        }
        service.onA11yEvent { onNewA11yEvent(it) }
    }

    val scope = service.scope

    var lastContentEventTime = 0L
    var lastEventTime = 0L
    val eventDeque = ArrayDeque<A11yEvent>()
    fun onNewA11yEvent(event: AccessibilityEvent) {
        if (event.eventType == CONTENT_CHANGED) {
            if (!service.isInteractive) return // 屏幕关闭后仍然有无障碍事件 type:2048, time:8094, app:com.miui.aod, cls:android.widget.TextView
            if (event.packageName == systemUiAppId && event.packageName != topActivityFlow.value.appId) return
        }
        // 过滤部分输入法事件
        if (event.packageName == imeAppId && topActivityFlow.value.appId != imeAppId) {
            if (event.recordCount == 0 && event.action == 0 && !event.isFullScreen) return
        }
        // 直接丢弃自身事件，自行更新 topActivity
        if ((event.eventType == CONTENT_CHANGED || !isActivityVisible()) && event.packageName == META.appId) return

        val a11yEvent = event.toA11yEvent() ?: return
        if (a11yEvent.type == CONTENT_CHANGED) {
            // 防止 content 类型事件过快
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
            // 某些应用会发送负时间事件, 直接丢弃
            // type:32, time:-104, app:com.miui.home, cls:com.miui.home.launcher.Launcher
            return
        }
        lastEventTime = event.eventTime
        synchronized(eventDeque) { eventDeque.addLast(a11yEvent) }
        scope.launch(eventDispatcher) { consumeEvent(a11yEvent) }
    }

    val queryEvents = mutableListOf<A11yEvent>()
    suspend fun consumeEvent(headEvent: A11yEvent) {
        val consumedEvents = synchronized(eventDeque) {
            if (eventDeque.firstOrNull() !== headEvent) return
            eventDeque.filter { it.sameAs(headEvent) }.apply {
                repeat(size) { eventDeque.removeFirst() }
            }
        }
        val latestEvent = consumedEvents.last()
        val evAppId = latestEvent.appId
        val evActivityId = latestEvent.name
        val oldAppId = topActivityFlow.value.appId
        val rightAppId = if (oldAppId == evAppId) {
            evAppId
        } else {
            getTimeoutAppId() ?: return
        }
        if (rightAppId == evAppId) {
            if (latestEvent.type == STATE_CHANGED) {
                // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                if (isActivity(evAppId, evActivityId)) {
                    updateTopActivity(evAppId, evActivityId)
                }
            }
        }
        if (rightAppId != topActivityFlow.value.appId) {
            // 从 锁屏，下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
            val topCpn = shizukuContextFlow.value.topCpn()
            if (topCpn?.packageName == rightAppId) {
                updateTopActivity(topCpn.packageName, topCpn.className)
            } else {
                updateTopActivity(rightAppId, null)
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

    var lastGetAppIdTime = 0L
    var lastAppId: String? = null
    suspend fun getTimeoutAppId(): String? {
        if (lastAppId != null && System.currentTimeMillis() - lastGetAppIdTime <= 100) return lastAppId
        // 某些应用通过无障碍获取 safeActiveWindow 耗时长，导致多个事件连续堆积堵塞，无法检测到 appId 切换导致状态异常
        // https://github.com/gkd-kit/gkd/issues/622
        lastAppId = withTimeoutOrNull(100) {
            runInterruptible(Dispatchers.IO) { service.safeActiveWindowAppId }
        } ?: shizukuContextFlow.value.topCpn()?.packageName
        lastGetAppIdTime = System.currentTimeMillis()
        return lastAppId
    }

    // 某些场景耗时 5000 ms
    suspend fun getTimeoutActiveWindow(): AccessibilityNodeInfo? = suspendCoroutine { s ->
        val temp = atomic<Continuation<AccessibilityNodeInfo?>?>(s)
        scope.launch(Dispatchers.IO) {
            delay(500L)
            temp.getAndUpdate { null }?.resume(null)
        }
        scope.launch(Dispatchers.IO) {
            val a = service.safeActiveWindow
            temp.getAndUpdate { null }?.resume(a)
        }
    }


    @Volatile
    var querying = false

    @Synchronized
    fun startQueryJob(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        byDelayRule: ResolvedRule? = null,
    ) {
        if (!storeFlow.value.enableMatch) return
        if (activityRuleFlow.value.currentRules.isEmpty()) return
        if (querying) return
        // 刚启动时获取 safeActiveWindow 非常耗时
        if (byEvent == null && service.justStarted) return
        scope.launchTry(queryDispatcher) {
            querying = true
            val st = System.currentTimeMillis()
            try {
                Log.d(
                    "A11yRuleEngine",
                    "startQueryJob start byEvent=${byEvent != null}, byForced=$byForced, byDelayRule=${byDelayRule != null}"
                )
                queryAction(byEvent, byForced, byDelayRule)
            } finally {
                val et = System.currentTimeMillis() - st
                Log.d("A11yRuleEngine", "startQueryJob end $et ms")
                querying = false
            }
        }
    }

    fun checkFutureStartJob() {
        val t = System.currentTimeMillis()
        if (t - lastTriggerTime < 3000L || t - appChangeTime < 5000L) {
            scope.launch(actionDispatcher) {
                delay(300)
                startQueryJob()
            }
        } else if (activityRuleFlow.value.hasFeatureAction) {
            scope.launch(actionDispatcher) {
                delay(300)
                startQueryJob(byForced = true)
            }
        }
    }

    fun fixAppId(rightAppId: String) {
        if (topActivityFlow.value.appId == rightAppId) return
        val topCpn = shizukuContextFlow.value.topCpn()
        if (topCpn?.packageName == rightAppId) {
            updateTopActivity(topCpn.packageName, topCpn.className)
        } else {
            updateTopActivity(rightAppId, null)
        }
        scope.launch(actionDispatcher) {
            delay(300)
            startQueryJob()
        }
    }

    suspend fun queryAction(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        delayRule: ResolvedRule? = null,
    ) {
        val newEvents = if (delayRule != null) {// 延迟规则不消耗事件
            null
        } else {
            synchronized(queryEvents) {
                if (byEvent != null && queryEvents.isEmpty()) {
                    return checkFutureStartJob()
                }
                (if (queryEvents.size > 1) {
                    val hasDiffItem = queryEvents.any { e ->
                        queryEvents.any { e2 -> !e.sameAs(e2) }
                    }
                    if (hasDiffItem) {
                        // 存在不同的事件节点, 全部丢弃使用 root 查询
                        null
                    } else {
                        // type,appId,className 一致, 需要在 synchronized 外验证是否是同一节点
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
        val activityRule = activityRuleFlow.value
        activityRule.currentRules.forEach { rule ->
            if (rule.status == RuleStatus.Status3 && rule.matchDelayJob.value == null) {
                rule.matchDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.matchDelay)
                    rule.matchDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
            }
        }
        if (activityRule.skipMatch) {
            // 如果当前应用没有规则/暂停匹配, 则不去调用获取事件节点避免阻塞
            return checkFutureStartJob()
        }
        var lastNode = if (newEvents == null || newEvents.size <= 1) {
            newEvents?.firstOrNull()?.safeSource
        } else {
            // 获取最后两个事件, 如果最后两个事件的节点不一致, 则丢弃
            // 相等则是同一个节点发出的连续事件, 常见于倒计时界面
            val lastNode = newEvents.last().safeSource
            if (lastNode == null || lastNode == newEvents[0].safeSource) {
                lastNode
            } else {
                null
            }
        }
        var lastNodeUsed = false
        if (!a11yContext.clearOldAppNodeCache()) {
            if (byEvent != null) { // 此为多数情况
                // 新事件到来时, 若缓存清理不及时会导致无法查询到节点
                a11yContext.clearNodeCache(lastNode)
            }
        }
        for (rule in activityRule.priorityRules) { // 规则数量有可能过多导致耗时过长
            if (activityRule !== activityRuleFlow.value) break
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
            if (topActivityFlow.value.appId != rightAppId || (!matchApp && rule is AppRule)) {
                scope.launch(eventDispatcher) { fixAppId(rightAppId) }
                return checkFutureStartJob()
            }
            if (!matchApp) continue
            val target = a11yContext.queryRule(rule, nodeVal) ?: continue
            if (activityRule !== activityRuleFlow.value) break
            if (rule.checkDelay() && rule.actionDelayJob.value == null) {
                rule.actionDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.actionDelay)
                    rule.actionDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
                continue
            }
            if (rule.status != RuleStatus.StatusOk) break
            val actionResult = rule.performAction(target)
            if (actionResult.result) {
                val topActivity = topActivityFlow.value
                rule.trigger()
                scope.launch(actionDispatcher) {
                    delay(300)
                    startQueryJob()
                }
                if (actionResult.action != ActionPerformer.None.action) {
                    showActionToast()
                }
                addActionLog(rule, topActivity, target, actionResult)
            }
        }
        checkFutureStartJob()
    }
}
