package li.songe.gkd.service

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.NetworkUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import li.songe.gkd.composition.CompositionAbService
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.ClickAction
import li.songe.gkd.data.ClickLog
import li.songe.gkd.data.NodeInfo
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.click
import li.songe.gkd.data.clickCenter
import li.songe.gkd.data.clickNode
import li.songe.gkd.db.DbSet
import li.songe.gkd.shizuku.useSafeGetTasksFc
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.increaseClickCount
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.launchWhile
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.selector.Selector
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@OptIn(FlowPreview::class)
class GkdAbService : CompositionAbService({
    useLifeCycleLog()

    val context = this as GkdAbService

    val scope = useScope()

    service = context
    onDestroy {
        service = null
        topActivityFlow.value = null
    }

    ManageService.start(context)
    onDestroy {
        ManageService.stop(context)
    }

    val safeGetTasksFc = useSafeGetTasksFc(scope)
    fun getActivityIdByShizuku(): String? {
        if (!storeFlow.value.enableShizuku) return null
        return safeGetTasksFc()?.lastOrNull()?.topActivity?.className
    }

    fun isActivity(appId: String, activityId: String): Boolean {
        return activityId.startsWith(appId) || (try {
            packageManager.getActivityInfo(ComponentName(appId, activityId), 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } != null)
    }

    onAccessibilityEvent { event -> // 根据事件获取 activityId, 概率不准确
        if (event == null) return@onAccessibilityEvent

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val newAppId = event.packageName?.toString() ?: return@onAccessibilityEvent
            val newActivityId = event.className?.toString() ?: return@onAccessibilityEvent
            val rightAppId =
                safeActiveWindow?.packageName?.toString() ?: return@onAccessibilityEvent
            val shizukuActivityId = getActivityIdByShizuku() ?: topActivityFlow.value?.activityId
            if (rightAppId != newAppId) {
                topActivityFlow.value =
                    TopActivity(appId = rightAppId, activityId = shizukuActivityId)
            } else {
                if (isActivity(newAppId, newActivityId)) {
                    topActivityFlow.value = TopActivity(newAppId, newActivityId)
                } else {
                    if (newActivityId.startsWith("android.") || newActivityId.startsWith("androidx.") || newActivityId.startsWith(
                            "com.android."
                        )
                    ) {
                        topActivityFlow.value = topActivityFlow.value?.copy(
                            appId = rightAppId, activityId = shizukuActivityId, sourceId = null
                        )
                    } else {
                        topActivityFlow.value = topActivityFlow.value?.copy(
                            appId = rightAppId,
                            activityId = shizukuActivityId,
                            sourceId = newActivityId
                        )
                    }
                }
            }
        }
    }

    scope.launchWhile(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, 0)
        launcherActivityIdFlow.value = info?.activityInfo?.name
        delay(10 * 60_000)
    }

    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?

    scope.launchWhile(Dispatchers.Default) { // 屏幕无障碍信息轮询
        delay(200)

        if (km?.isKeyguardLocked == true) { // isScreenLock
            return@launchWhile
        }

        val rightAppId = safeActiveWindow?.packageName?.toString()
        if (rightAppId != null && rightAppId != topActivityFlow.value?.appId) {
            topActivityFlow.value = topActivityFlow.value?.copy(
                appId = rightAppId,
                activityId = getActivityIdByShizuku() ?: topActivityFlow.value?.activityId
            )
        }

        if (!storeFlow.value.enableService) return@launchWhile

        val currentRules = currentRulesFlow.value
        for (rule in currentRules) {
            if (!isAvailableRule(rule)) continue
            val target = rule.query(safeActiveWindow) ?: continue

            // 开始延迟
            if (rule.delay > 0 && rule.delayTriggerTime == 0L) {
                rule.triggerDelay()
                continue
            }

            // 如果节点在屏幕外部, click 的结果为 null

            val actionResult = rule.performAction(context, target)
            if (actionResult.result) {
                toastClickTip()
                rule.trigger()
                scope.launchTry(Dispatchers.IO) {
                    LogUtils.d(
                        *rule.matches.toTypedArray(),
                        NodeInfo.abNodeToNode(target).attr,
                        actionResult
                    )
                    val clickLog = ClickLog(
                        appId = topActivityFlow.value?.appId,
                        activityId = topActivityFlow.value?.activityId,
                        subsId = rule.subsItem.id,
                        groupKey = rule.group.key,
                        ruleIndex = rule.index,
                        ruleKey = rule.key
                    )
                    DbSet.clickLogDb.clickLogDao().insert(clickLog)
                    increaseClickCount()
                }
            }
            if (currentRules !== currentRulesFlow.value) break
        }

    }

    var lastUpdateSubsTime = System.currentTimeMillis()
    scope.launchWhile(Dispatchers.IO) { // 自动从网络更新订阅文件
        delay(10 * 60_000) // 每 10 分钟检查一次
        if (ScreenUtils.isScreenLock() // 锁屏
            || storeFlow.value.updateSubsInterval <= 0 // 暂停更新
            || System.currentTimeMillis() - lastUpdateSubsTime < storeFlow.value.updateSubsInterval.coerceAtLeast(
                60 * 60_000
            ) // 距离上次更新的时间小于更新间隔
        ) {
            return@launchWhile
        }
        if (!NetworkUtils.isAvailable()) {// 产生 io
            return@launchWhile
        }

        subsItemsFlow.value.forEach { subsItem ->
            if (subsItem.updateUrl == null) return@forEach
            try {
                val newSubsRaw =
                    SubscriptionRaw.parse(Singleton.client.get(subsItem.updateUrl).bodyAsText())
                if (newSubsRaw.id != subsItem.id) {
                    return@forEach
                }
                val oldSubsRaw = subsIdToRawFlow.value[subsItem.id]
                if (oldSubsRaw != null && newSubsRaw.version <= oldSubsRaw.version) {
                    return@forEach
                }
                subsItem.subsFile.writeText(
                    SubscriptionRaw.stringify(
                        newSubsRaw
                    )
                )
                val newItem = subsItem.copy(
                    updateUrl = newSubsRaw.updateUrl ?: subsItem.updateUrl,
                    mtime = System.currentTimeMillis()
                )
                DbSet.subsItemDao.update(newItem)
                LogUtils.d("更新磁盘订阅文件:${newSubsRaw.name}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lastUpdateSubsTime = System.currentTimeMillis()
    }

    scope.launch {
        combine(topActivityFlow, currentRulesFlow) { topActivity, currentRules ->
            topActivity to currentRules
        }.debounce(300).collect { (topActivity, currentRules) ->
            if (storeFlow.value.enableService) {
                LogUtils.d(
                    topActivity, *currentRules.map { r -> r.rule.matches }.toTypedArray()
                )
            } else {
                LogUtils.d(
                    topActivity
                )
            }
        }
    }

}) {

    companion object {
        var service: GkdAbService? = null
        fun isRunning() = ServiceUtils.isServiceRunning(GkdAbService::class.java)

        fun execClickAction(clickAction: ClickAction): ActionResult {
            val serviceVal = service ?: throw RpcError("无障碍没有运行")
            val selector = try {
                Selector.parse(clickAction.selector)
            } catch (e: Exception) {
                throw RpcError("非法选择器")
            }

            val targetNode =
                serviceVal.safeActiveWindow?.querySelector(selector, clickAction.quickFind)
                    ?: throw RpcError("没有选择到节点")

            return when (clickAction.action) {
                "clickNode" -> clickNode(serviceVal, targetNode)
                "clickCenter" -> clickCenter(serviceVal, targetNode)
                else -> click(serviceVal, targetNode)
            }
        }


        suspend fun currentScreenshot() = service?.run {
            suspendCoroutine {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshot(Display.DEFAULT_DISPLAY,
                        application.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                it.resume(
                                    Bitmap.wrapHardwareBuffer(
                                        screenshot.hardwareBuffer, screenshot.colorSpace
                                    )
                                )
                            }

                            override fun onFailure(errorCode: Int) = it.resume(null)
                        })
                } else {
                    it.resume(null)
                }
            }
        }
    }
}