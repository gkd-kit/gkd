package li.gkd.app.notif

import li.gkd.app.rule.RuleSummary
import li.gkd.app.state.Loadable

fun String.replaceNotificationTemplate(ruleSummary: RuleSummary, count: Long): String =
    formatNotificationTemplate(
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
    state.value?.let { replaceNotificationTemplate(it, count) } ?: RuleSummary.statusText(state, count)

fun formatNotificationTemplate(template: String, global: Int?, apps: Int?, groups: Int?, actions: Long): String = template
    .replace("\${i}", global?.toString() ?: "\${i}")
    .replace("\${k}", apps?.toString() ?: "\${k}")
    .replace("\${u}", groups?.toString() ?: "\${u}")
    .replace("\${n}", actions.toString())
