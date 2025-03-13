package li.songe.gkd.data

sealed class ResolvedGroup(
    open val group: RawSubscription.RawGroupProps,
    val subscription: RawSubscription,
    val subsItem: SubsItem,
    val config: SubsConfig?,
) {
    val excludeData = ExcludeData.parse(config?.exclude)

    abstract val appId: String?
}

class ResolvedAppGroup(
    override val group: RawSubscription.RawAppGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsConfig?,
    val app: RawSubscription.RawApp,
    val enable: Boolean,
    val category: RawSubscription.RawCategory?,
    val categoryConfig: CategoryConfig?,
) : ResolvedGroup(group, subscription, subsItem, config) {
    override val appId: String?
        get() = app.id
}

class ResolvedGlobalGroup(
    override val group: RawSubscription.RawGlobalGroup,
    subscription: RawSubscription,
    subsItem: SubsItem,
    config: SubsConfig?,
) : ResolvedGroup(group, subscription, subsItem, config) {
    override val appId: String?
        get() = null
}