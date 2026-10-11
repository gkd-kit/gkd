package li.gkd.app.ui.home

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
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

}
