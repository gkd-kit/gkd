package li.gkd.app.ui.share

import li.gkd.app.text.UiStrings
import li.gkd.app.domain.rule.RuleSummary

val RuleSummary.numText: String
    get() = if (globalGroups.size + appGroupSize > 0) {
        if (globalGroups.isNotEmpty()) {
            UiStrings.subscription_global_count(globalGroups.size) + if (appGroupSize > 0) "/" else ""
        } else {
            ""
        } + if (appGroupSize > 0) {
            UiStrings.subscription_app_rule_counts(appSize, appGroupSize)
        } else {
            ""
        }
    } else {
        UiStrings.rules_empty
    }

fun RuleSummary.statusText(actionCount: Long): String = if (actionCount > 0) {
    UiStrings.action_count_summary(numText, actionCount)
} else {
    numText
}
