package li.songe.gkd.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.service.lastTriggerRuleFlow
import li.songe.gkd.service.launcherActivityIdFlow
import li.songe.gkd.service.querySelector
import li.songe.selector.Selector

data class Rule(
    /**
     * length>0
     */
    val matches: List<Selector> = emptyList(),
    val excludeMatches: List<Selector> = emptyList(),
    /**
     * 任意一个元素是上次点击过的
     */
    val preRules: Set<Rule> = emptySet(),
    val actionCd: Long = defaultMiniCd,
    val actionDelay: Long = 0,
    val matchLauncher: Boolean = false,
    val quickFind: Boolean = false,

    val matchDelay: Long?,
    val matchTime: Long?,
    val actionMaximum: Int?,
    val resetMatch: String?,

    val appId: String,
    val activityIds: Set<String> = emptySet(),
    val excludeActivityIds: Set<String> = emptySet(),

    val key: Int? = null,
    val preKeys: Set<Int> = emptySet(),

    val index: Int = 0,
    val rule: SubscriptionRaw.RuleRaw,
    val group: SubscriptionRaw.GroupRaw,
    val app: SubscriptionRaw.AppRaw,
    val subsItem: SubsItem,
) {
    var actionDelayTriggerTime = 0L
    fun triggerDelay() {
        // 触发延迟, 一段时间内此规则不可利用
        actionDelayTriggerTime = System.currentTimeMillis()
    }

    var actionTriggerTime = 0L
    fun trigger() {
        actionTriggerTime = System.currentTimeMillis()
        // 重置延迟点
        actionDelayTriggerTime = 0L
        actionCount++
        lastTriggerRuleFlow.value = this
    }

    var actionCount = 0

    var matchChangeTime = 0L

    val matchAllTime = (matchTime ?: 0) + (matchDelay ?: 0)

    val resetMatchTypeWhenActivity = when (resetMatch) {
        "app" -> false
        "activity" -> true
        else -> true
    }


    fun query(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (nodeInfo == null) return null
        var target: AccessibilityNodeInfo? = null
        for (selector in matches) {
            target = nodeInfo.querySelector(selector, quickFind) ?: return null
        }
        for (selector in excludeMatches) {
            if (nodeInfo.querySelector(selector, quickFind) != null) return null
        }
        return target
    }

    private val matchAnyActivity = activityIds.isEmpty() && excludeActivityIds.isEmpty()

    fun matchActivityId(activityId: String?): Boolean {
        if (matchAnyActivity) return true
        if (activityId == null) return false
        if (excludeActivityIds.any { activityId.startsWith(it) }) return false
        if (activityIds.isEmpty()) return true
        if (matchLauncher && launcherActivityIdFlow.value == activityId) {
            return true
        }
        return activityIds.any { activityId.startsWith(it) }
    }

    val performAction = getActionFc(rule.action)

    companion object {
        const val defaultMiniCd = 1000L
    }
}

typealias ActionFc = (context: AccessibilityService, node: AccessibilityNodeInfo) -> ActionResult


@Serializable
data class GkdAction(
    val selector: String,
    val quickFind: Boolean = false,
    val action: String? = null,
)


@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
)

val click: ActionFc = { context, node ->
    if (node.isClickable) clickNode(context, node) else clickCenter(context, node)
}

val clickNode: ActionFc = { _, node ->
    ActionResult(
        action = "clickNode", result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    )
}

val clickCenter: ActionFc = { context, node ->
    val react = Rect()
    node.getBoundsInScreen(react)
    val x = (react.right + react.left) / 2f
    val y = (react.bottom + react.top) / 2f
    ActionResult(
        action = "clickCenter",
        result = if (0 <= x && 0 <= y && x <= ScreenUtils.getScreenWidth() && y <= ScreenUtils.getScreenHeight()) {
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(
                GestureDescription.StrokeDescription(
                    path, 0, ViewConfiguration.getTapTimeout().toLong()
                )
            )
            context.dispatchGesture(gestureDescription.build(), null, null)
            true
        } else {
            false
        }
    )

}

val backFc: ActionFc = { context, _ ->
    ActionResult(
        action = "back",
        result = context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    )
}

fun getActionFc(action: String?): ActionFc {
    return when (action) {
        "clickNode" -> clickNode
        "clickCenter" -> clickCenter
        "back" -> backFc
        else -> click
    }
}