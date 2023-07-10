package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.gkd.accessibility.querySelector
import li.songe.selector.Selector

data class Rule(
    /**
     * length>0
     */
    val matches: List<Selector> = emptyList(),
    val excludeMatches: List<Selector> = emptyList(),
    /**
     * 任意一个元素是上次触发过的
     */
    val preRules: Set<Rule> = emptySet(),
    val cd: Long = defaultMiniCd,
    val index: Int = 0,
    val appId: String = "",
    val activityIds: Set<String> = emptySet(),
    val excludeActivityIds: Set<String> = emptySet(),
    val key: Int? = null,
    val preKeys: Set<Int> = emptySet(),
) {
    private var triggerTime = 0L
    fun trigger() {
        triggerTime = System.currentTimeMillis()
    }

    val active: Boolean
        get() = triggerTime + cd < System.currentTimeMillis()

    val matchAnyActivity = activityIds.contains("*")

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

    companion object {
        const val defaultMiniCd = 1000L
    }
}