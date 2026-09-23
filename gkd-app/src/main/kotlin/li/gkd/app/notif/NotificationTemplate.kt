package li.gkd.app.notif

import li.gkd.app.domain.rule.RuleSummary

fun String.replaceNotificationTemplate(ruleSummary: RuleSummary, count: Long): String {
    return replace($$"${i}", ruleSummary.globalGroups.size.toString())
        .replace($$"${k}", ruleSummary.appSize.toString())
        .replace($$"${u}", ruleSummary.appGroupSize.toString())
        .replace($$"${n}", count.toString())
}
