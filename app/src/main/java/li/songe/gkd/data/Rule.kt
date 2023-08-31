package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
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
    private var triggerTime = 0L
    fun trigger() {
        triggerTime = System.currentTimeMillis()
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

    fun matchActivityId(activityId: String?): Boolean {
        if (activityId == null) return false
        if (excludeActivityIds.any { activityId.startsWith(it) }) return false
        if (activityIds.isEmpty()) return true
        return activityIds.any { activityId.startsWith(it) }
    }

    companion object {
        const val defaultMiniCd = 1000L
    }
}