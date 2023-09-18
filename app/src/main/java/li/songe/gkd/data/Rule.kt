package li.songe.gkd.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable
import li.songe.gkd.service.lastTriggerRuleFlow
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
    val cd: Long = defaultMiniCd,
    val delay: Long = 0,
    val index: Int = 0,

    val appId: String = "",
    val activityIds: Set<String> = emptySet(),
    val excludeActivityIds: Set<String> = emptySet(),

    val key: Int? = null,
    val preKeys: Set<Int> = emptySet(),

    val rule: SubscriptionRaw.RuleRaw,
    val group: SubscriptionRaw.GroupRaw,
    val app: SubscriptionRaw.AppRaw,
    val subsItem: SubsItem,
) {
    var delayTriggerTime = 0L
    fun triggerDelay() {
        // 触发延迟, 一段时间内此规则不可利用
        delayTriggerTime = System.currentTimeMillis()
    }

    private var triggerTime = 0L
    fun trigger() {
        triggerTime = System.currentTimeMillis()
        // 重置延迟点
        delayTriggerTime = 0L
        lastTriggerRuleFlow.value = this
    }

    val active: Boolean
        get() = triggerTime + cd < System.currentTimeMillis()


    fun query(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (nodeInfo == null) return null
        var target: AccessibilityNodeInfo? = null
        for (selector in matches) {
            target = nodeInfo.querySelector(selector) ?: return null
        }
        for (selector in excludeMatches) {
            if (nodeInfo.querySelector(selector) != null) return null
        }
        return target
    }

    private val matchAnyActivity = activityIds.isEmpty() && excludeActivityIds.isEmpty()

    fun matchActivityId(activityId: String?): Boolean {
        if (matchAnyActivity) return true
        if (activityId == null) return false
        if (excludeActivityIds.any { activityId.startsWith(it) }) return false
        if (activityIds.isEmpty()) return true
        return activityIds.any { activityId.startsWith(it) }
    }

    val performAction: ActionFc = when (rule.action) {
        "clickNode" -> clickNode
        "clickCenter" -> clickCenter
        else -> click
    }

    companion object {
        const val defaultMiniCd = 1000L
    }
}

typealias ActionFc = (context: AccessibilityService, node: AccessibilityNodeInfo) -> ActionResult


@Serializable
data class ClickAction(
    val selector: String,
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
    val x = react.left + 50f / 100f * (react.right - react.left)
    val y = react.top + 50f / 100f * (react.bottom - react.top)
    ActionResult(
        action = "clickCenter", result = if (x >= 0 && y >= 0) {
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            context.dispatchGesture(gestureDescription.build(), null, null)
            true
        } else {
            false
        }
    )

}