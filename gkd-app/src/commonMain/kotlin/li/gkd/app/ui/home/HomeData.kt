package li.gkd.app.ui.home

import li.gkd.app.model.AppInfo
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_count_summary
import li.gkd.app.resources.rules_empty
import li.gkd.app.resources.subscription_app_rule_counts
import li.gkd.app.resources.subscription_global_count
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.text.getSync
import li.gkd.db.ActionLog
import li.gkd.db.RuleGroupType

object HomeDataText {
    fun latest(
        record: ActionLog?,
        subscriptions: Map<Long, RawSubscription>,
        apps: Map<String, AppInfo>
    ): String? {
        record ?: return null
        val appRule = record.groupType == RuleGroupType.App
        val sub = subscriptions[record.subsId]
        val name =
            if (appRule) sub?.apps?.find { it.id == record.appId }?.groups?.find { it.key == record.groupKey }?.name
            else sub?.globalGroups?.find { it.key == record.groupKey }?.name
        val app = apps[record.appId]?.name ?: record.appId
        return when {
            name == null -> app; name.startsWith(app) -> name; appRule -> "$app/$name"; else -> "$name/$app"
        }
    }

    fun summary(global: Int, apps: Int, groups: Int, actions: Long): String {
        val rules = listOfNotNull(
            if (global > 0) Res.string.subscription_global_count.getSync(global) else null,
            if (groups > 0) Res.string.subscription_app_rule_counts.getSync(
                apps,
                groups
            ) else null,
        ).joinToString("/").ifEmpty { Res.string.rules_empty.getSync() }
        return if (actions > 0) Res.string.action_count_summary.getSync(
            rules,
            actions
        ) else rules
    }

    fun format(template: String, global: Int?, apps: Int?, groups: Int?, actions: Long) = template
        .replace("\${i}", global?.toString() ?: "\${i}")
        .replace("\${k}", apps?.toString() ?: "\${k}")
        .replace("\${u}", groups?.toString() ?: "\${u}").replace("\${n}", actions.toString())
}
