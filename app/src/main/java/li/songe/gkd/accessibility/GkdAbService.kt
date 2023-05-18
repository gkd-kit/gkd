package li.songe.gkd.accessibility

import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.NetworkUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import li.songe.gkd.composition.CompositionAbService
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.data.RuleManager
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.debug.server.api.Node
import li.songe.gkd.util.Ext.buildRuleManager
import li.songe.gkd.util.Ext.getActivityIdByShizuku
import li.songe.gkd.util.Ext.getSubsFileLastModified
import li.songe.gkd.util.Ext.launchWhile
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.Storage
import li.songe.selector_android.GkdSelector
import java.io.File

class GkdAbService : CompositionAbService({
    useLifeCycleLog()

    val context = this as GkdAbService

    val scope = useScope()

    service = context
    onDestroy { service = null }

    KeepAliveService.start(context)
    onDestroy {
        KeepAliveService.stop(context)
    }

    var serviceConnected = false
    onServiceConnected { serviceConnected = true }
    onInterrupt { serviceConnected = false }

    onAccessibilityEvent { event ->
        val activityId = event?.className?.toString() ?: return@onAccessibilityEvent
        val rootAppId = rootInActiveWindow?.packageName?.toString() ?: return@onAccessibilityEvent
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
//                在桌面和应用之间来回切换, 大概率导致识别失败
                if (!activityId.startsWith("android.") &&
                    !activityId.startsWith("androidx.") &&
                    !activityId.startsWith("com.android.")
                ) {
                    if ((activityId == "com.miui.home.launcher.Launcher" && rootAppId != "com.miui.home")) {
//                        小米手机 上滑手势, 导致 活动名 不属于包名
//                        另外 微信扫码登录第三方网站 也会导致失败
                    } else {
                        if (activityId != nodeSnapshot.activityId) {
                            nodeSnapshot = nodeSnapshot.copy(
                                activityId = activityId
                            )
                        }
                    }
                }
            }

            else -> {}
        }
    }

    scope.launchWhile {
        delay(300)
        val activityId = getActivityIdByShizuku() ?: return@launchWhile
        if (activityId != nodeSnapshot.activityId) {
            nodeSnapshot = nodeSnapshot.copy(
                activityId = activityId
            )
        }
    }

    var subsFileLastModified = 0L
    scope.launchWhile { // 根据本地文件最新写入时间 决定 是否 更新数据
        val t = getSubsFileLastModified()
        if (t > subsFileLastModified) {
            subsFileLastModified = t
            ruleManager = buildRuleManager()
            LogUtils.d("读取本地规则")
        }
        delay(10_000)
    }

    scope.launchWhile {
        if (!serviceConnected) return@launchWhile
        if (!Storage.settings.enableService || ScreenUtils.isScreenLock()) return@launchWhile

        nodeSnapshot = nodeSnapshot.copy(
            root = rootInActiveWindow,
        )
        val shot = nodeSnapshot
        if (shot.root == null) return@launchWhile

        for (rule in ruleManager.match(shot.appId, shot.activityId)) {
            val target = rule.query(shot.root) ?: continue
            val clickResult = GkdSelector.click(target, context)
            ruleManager.trigger(rule)
            LogUtils.d(
                *rule.matches.toTypedArray(),
                Node.info2data(target),
                clickResult
            )
        }
        delay(200)
    }

    scope.launchWhile {
        delay(5000)
        RoomX.select<SubsItem>().map { subsItem ->
            if (!NetworkUtils.isAvailable()) return@map
            try {
                val text = Singleton.client.get(subsItem.updateUrl).bodyAsText()
                val subscriptionRaw = SubscriptionRaw.parse5(text)
                if (subscriptionRaw.version <= subsItem.version) {
                    return@map
                }
                val newItem = subsItem.copy(
                    updateUrl = subscriptionRaw.updateUrl
                        ?: subsItem.updateUrl,
                    name = subscriptionRaw.name,
                    mtime = System.currentTimeMillis()
                )
                RoomX.update(newItem)
                File(newItem.filePath).writeText(
                    SubscriptionRaw.stringify(
                        subscriptionRaw
                    )
                )
                LogUtils.d("更新订阅文件:${subsItem.name}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        delay(30 * 60_000)
    }

}) {
    private var nodeSnapshot = NodeSnapshot()
        set(value) {
            if (field.appId != value.appId || field.activityId != value.activityId) {
                LogUtils.d(
                    value.appId,
                    value.activityId,
                    *ruleManager.match(value.appId, value.activityId).toList().toTypedArray()
                )
            }
            field = value
        }

    private var ruleManager = RuleManager()

    companion object {
        fun isRunning() = ServiceUtils.isServiceRunning(GkdAbService::class.java)
        fun currentNodeSnapshot() = service?.nodeSnapshot
        private var service: GkdAbService? = null
    }
}