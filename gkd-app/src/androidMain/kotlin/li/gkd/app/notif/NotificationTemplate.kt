package li.gkd.app.notif

import li.gkd.app.rule.RuleSummary
import li.gkd.app.state.Loadable
import li.gkd.app.ui.home.HomeDataText
import li.gkd.app.ui.share.statusText

fun String.replaceNotificationTemplate(ruleSummary: RuleSummary, count: Long): String =
    HomeDataText.format(
        this,
        ruleSummary.globalGroups.size,
        ruleSummary.appSize,
        ruleSummary.appGroupSize,
        count
    )

fun String.replaceNotificationTemplate(
    state: Loadable<RuleSummary>,
    count: Long
): String =
    state.value?.let { replaceNotificationTemplate(it, count) } ?: state.statusText(count)
