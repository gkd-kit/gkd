package li.gkd.app.matching

import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleStatus
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.TopActivity

@kotlinx.serialization.Serializable
enum class RuleAvailability {
    Loading,
    Ready,
    Failed,
}

class ForegroundRuleSnapshot(
    val topActivity: TopActivity = TopActivity(),
    val blockMatch: Boolean,
    val ruleSummary: RuleSummary = RuleSummary(),
    val ruleAvailability: RuleAvailability = RuleAvailability.Ready,
) {
    val appRules = ruleSummary.appIdToRules[topActivity.appId] ?: emptyList()
    val activityRules =
        if (blockMatch) emptyList()
        else
            appRules.filter { rule ->
                rule.matchActivity(topActivity.appId, topActivity.activityId)
            }
    val globalRules =
        if (blockMatch) emptyList()
        else
            ruleSummary.globalRules.filter { r ->
                r.matchActivity(topActivity.appId, topActivity.activityId)
            }

    val currentRules = (activityRules + globalRules).sortedBy { it.order }
    val emptyReason: String
        get() =
            when {
                ruleAvailability == RuleAvailability.Loading -> "rules_loading"
                ruleAvailability == RuleAvailability.Failed -> "rules_load_failed"
                blockMatch -> "app_blocked"
                else -> "no_rules_for_activity"
            }

    val hasPriorityRule = currentRules.size > 1 && currentRules.any { it.priorityEnabled }
    val activePriority: Boolean
        get() = hasPriorityRule && currentRules.any { it.isPriority() }

    val priorityRules: List<ResolvedRule>
        get() =
            if (hasPriorityRule) {
                currentRules.sortedBy { if (it.isPriority()) 0 else 1 }
            } else {
                currentRules
            }

    val skipMatch: Boolean
        get() {
            return currentRules.all { r -> !r.status.ok }
        }

    val skipConsumeEvent: Boolean
        get() {
            return currentRules.all { r -> !r.status.alive }
        }

    val hasFeatureAction: Boolean
        get() = currentRules.any { r ->
            r.checkForced() && (r.status == RuleStatus.Ready || r.status == RuleStatus.Cooldown)
        }
}
