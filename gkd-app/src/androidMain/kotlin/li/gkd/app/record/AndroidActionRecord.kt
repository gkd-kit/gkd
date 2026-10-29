package li.gkd.app.record

import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.appScope
import li.gkd.app.data.toAttrInfo
import li.gkd.app.logging.LogUtils
import li.gkd.app.logging.logText
import li.gkd.app.model.ActionResult
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.TopActivity
import li.gkd.app.util.launchLogged

fun addActionLog(
    rule: ResolvedRule,
    topActivity: TopActivity,
    target: AccessibilityNodeInfo,
    actionResult: ActionResult,
) {
    val input =
        ActionRecordInput(
            appId = topActivity.appId,
            activityId = topActivity.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = rule.g.group.groupType,
            ruleIndex = rule.index,
            ruleKey = rule.key,
            time = System.currentTimeMillis(),
        )
    appScope.launchLogged { RuntimeRecordRepository.recordAction(input) }
    // Logging must not turn a completed action into a failure.
    try {
        val attributes =
            target.toAttrInfo(0, 0).let {
                it.copy(
                    text = it.text?.let { text -> logText(text) },
                    desc = it.desc?.let { text -> logText(text) },
                )
            }
        LogUtils.i(rule.statusText(), attributes, actionResult)
    } catch (error: Exception) {
        LogUtils.w("Action node log unavailable", error)
    }
}
