package li.gkd.app.rule

import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubsItem

sealed class ResolvedGroup(
    open val group: RawSubscription.RawGroupProps,
    val subscription: RawSubscription,
    val subsItem: SubsItem,
    val config: SubsGroupConfig?,
) {
    val excludeData by lazy { ExcludeData.parse(config?.exclude) }

    abstract val appId: String?
}

class ResolvedAppGroup(
    override val group: RawSubscription.RawAppGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsGroupConfig?,
    val app: RawSubscription.RawApp,
    val enable: Boolean,
) : ResolvedGroup(group, subscription, subsItem, config) {
    override val appId: String?
        get() = app.id
}

class ResolvedGlobalGroup(
    override val group: RawSubscription.RawGlobalGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsGroupConfig?,
) : ResolvedGroup(group, subscription, subsItem, config) {
    override val appId: String?
        get() = null

    val groupExcludeAppIds
        get() = subscription.globalGroupAppGroupNameDisableMap[group.key] ?: emptySet()
}
