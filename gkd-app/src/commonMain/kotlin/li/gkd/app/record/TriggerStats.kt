package li.gkd.app.record

import li.gkd.app.rule.RuleGroupTarget
import li.gkd.db.RuleGroupType
import li.gkd.db.RuleTriggerStats

data class TriggerSummary(val count: Long = 0, val lastTime: Long? = null)

/** One database emission supplies all totals shown together on a page. */
class TriggerStats(rows: List<RuleTriggerStats>) {
    val total = rows.summarizeTriggers()
    val byApp = rows.groupBy { it.appId }.mapValues { (_, records) -> records.summarizeTriggers() }
    val byGroup = rows.groupBy {
        if (it.groupType == RuleGroupType.App) RuleGroupTarget.App(it.subsId, it.appId, it.groupKey)
        else RuleGroupTarget.Global(it.subsId, it.groupKey)
    }.mapValues { (_, records) -> records.summarizeTriggers() }

    fun app(appId: String): TriggerSummary = byApp[appId] ?: TriggerSummary()
    fun group(target: RuleGroupTarget): TriggerSummary = byGroup[if (target is RuleGroupTarget.Global) target.copy(pageAppId = null) else target] ?: TriggerSummary()
}

fun List<RuleTriggerStats>.summarizeTriggers() =
    TriggerSummary(sumOf { it.triggerCount }, maxOfOrNull { it.lastTriggerTime })
