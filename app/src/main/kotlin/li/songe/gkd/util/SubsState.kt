package li.songe.gkd.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.GlobalRule
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.ResolvedAppGroup
import li.songe.gkd.data.ResolvedGlobalGroup
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import java.net.URI

val subsItemsFlow by lazy {
    DbSet.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

private fun getCheckUpdateUrl(
    subsItem: SubsItem,
    subscription: RawSubscription?,
): String? {
    val checkUpdateUrl = subscription?.checkUpdateUrl ?: return null
    val updateUrl = subscription.updateUrl ?: subsItem.updateUrl ?: return checkUpdateUrl
    try {
        return URI(updateUrl).resolve(checkUpdateUrl).toString()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

sealed class SubsEntryType {
    abstract val subsItem: SubsItem
    abstract val subscription: RawSubscription?
    val checkUpdateUrl by lazy { getCheckUpdateUrl(subsItem, subscription) }
}

data class SubsEntry(
    override val subsItem: SubsItem,
    override val subscription: RawSubscription?,
) : SubsEntryType()

data class UsedSubsEntry(
    override val subsItem: SubsItem,
    override val subscription: RawSubscription,
) : SubsEntryType()

val subsMapFlow by lazy {
    SubscriptionStore.snapshotFlow.map { it.value?.subscriptions.orEmpty() }
        .stateIn(
            appScope,
            SharingStarted.Eagerly,
            SubscriptionStore.snapshotFlow.value.value?.subscriptions.orEmpty(),
        )
}
val subsLoadErrorsFlow by lazy {
    SubscriptionStore.snapshotFlow.map { it.value?.loadErrors.orEmpty() }
        .stateIn(
            appScope,
            SharingStarted.Eagerly,
            SubscriptionStore.snapshotFlow.value.value?.loadErrors.orEmpty(),
        )
}
val subsRefreshErrorsFlow by lazy {
    SubscriptionStore.snapshotFlow.map { it.value?.updateErrors.orEmpty() }
        .stateIn(
            appScope,
            SharingStarted.Eagerly,
            SubscriptionStore.snapshotFlow.value.value?.updateErrors.orEmpty(),
        )
}

val latestRecordFlow by lazy {
    DbSet.actionLogDao.queryLatest().stateIn(appScope, SharingStarted.Eagerly, null)
}
val latestRecordDescFlow by lazy {
    combine(
        latestRecordFlow,
        subsMapFlow,
        appInfoMapFlow,
    ) { record, subsMap, appMap ->
        if (record == null) return@combine null
        val isAppRule = record.groupType == SubsConfig.AppGroupType
        val groupName = if (isAppRule) {
            subsMap[record.subsId]?.apps?.find { a -> a.id == record.appId }?.groups?.find { g -> g.key == record.groupKey }?.name
        } else {
            subsMap[record.subsId]?.globalGroups?.find { g -> g.key == record.groupKey }?.name
        }
        val appName = appMap[record.appId]?.name
        val appShowName = appName ?: record.appId
        if (groupName != null) {
            if (groupName.startsWith(appShowName)) {
                groupName
            } else {
                if (isAppRule) {
                    "$appShowName/$groupName"
                } else {
                    "$groupName/$appShowName"
                }
            }
        } else {
            appShowName
        }
    }.stateIn(appScope, SharingStarted.Eagerly, null)
}

fun buildSubsEntries(
    items: List<SubsItem>,
    subscriptions: Map<Long, RawSubscription>,
): List<SubsEntry> = items.map { item ->
    SubsEntry(
        subsItem = item,
        subscription = subscriptions[item.id],
    )
}

fun buildUsedSubsEntries(entries: List<SubsEntry>): List<UsedSubsEntry> =
    entries.mapNotNull { entry ->
        entry.subscription?.takeIf { entry.subsItem.enable && it.hasRule }?.let { subscription ->
            UsedSubsEntry(entry.subsItem, subscription)
        }
    }

val subsEntriesFlow by lazy {
    combine(
        subsItemsFlow,
        subsMapFlow,
    ) { subsItems, subsIdToRaw ->
        buildSubsEntries(subsItems, subsIdToRaw)
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        buildSubsEntries(subsItemsFlow.value, subsMapFlow.value),
    )
}

val usedSubsEntriesFlow by lazy {
    subsEntriesFlow.map(::buildUsedSubsEntries).stateIn(
        appScope,
        SharingStarted.Eagerly,
        buildUsedSubsEntries(subsEntriesFlow.value),
    )
}

fun getCategoryEnable(
    category: RawSubscription.RawCategory?,
    categoryConfig: CategoryConfig?,
): Boolean? = if (categoryConfig != null) {
    // 批量配置
    categoryConfig.enable
} else {
    // 批量默认
    category?.enable
}

fun getGroupEnable(
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    category: RawSubscription.RawCategory? = null,
    categoryConfig: CategoryConfig? = null,
): Boolean = group.valid && when (group) {
    // 优先级: 规则用户配置 > 批量配置 > 批量默认 > 规则默认
    is RawSubscription.RawAppGroup -> {
        subsConfig?.enable ?: getCategoryEnable(category, categoryConfig) ?: group.enable ?: true
    }

    is RawSubscription.RawGlobalGroup -> {
        subsConfig?.enable ?: group.enable ?: true
    }
}

data class RuleSummary(
    val globalRules: List<GlobalRule> = emptyList(),
    val globalGroups: List<ResolvedGlobalGroup> = emptyList(),
    val appIdToRules: Map<String, List<AppRule>> = emptyMap(),
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>> = emptyMap(),
    val appIdToAllGroups: Map<String, List<ResolvedAppGroup>> = emptyMap(),
) {
    val appSize = appIdToRules.keys.size
    val appGroupSize = appIdToGroups.values.sumOf { s -> s.size }

    val numText = if (globalGroups.size + appGroupSize > 0) {
        if (globalGroups.isNotEmpty()) {
            "${globalGroups.size}全局" + if (appGroupSize > 0) {
                "/"
            } else {
                ""
            }
        } else {
            ""
        } + if (appGroupSize > 0) {
            "${appSize}应用/${appGroupSize}规则"
        } else {
            ""
        }
    } else {
        EMPTY_RULE_TIP
    }

    val slowGlobalGroups =
        globalRules.filter { r -> r.isSlow }.distinctBy { r -> r.group }
            .map { r -> r.group to r }
    val slowAppGroups =
        appIdToRules.values.flatten().filter { r -> r.isSlow }.distinctBy { r -> r.group }
            .map { r -> r.group to r }
    val slowGroupCount = slowGlobalGroups.size + slowAppGroups.size
}

val ruleSummaryFlow by lazy {
    combine(
        usedSubsEntriesFlow,
        appInfoMapFlow,
        DbSet.appConfigDao.queryUsedList(),
        DbSet.subsConfigDao.queryUsedList(),
        DbSet.categoryConfigDao.queryUsedList(),
    ) { subsEntries, appInfoCache, appConfigs, subsConfigs, categoryConfigs ->
        val globalSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.GlobalGroupType }
        val groupSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppGroupType }
        val appRules = HashMap<String, MutableList<AppRule>>()
        val appGroups = HashMap<String, List<RawSubscription.RawAppGroup>>()
        val appAllGroups =
            HashMap<String, List<ResolvedAppGroup>>()
        val globalRules = mutableListOf<GlobalRule>()
        val globalGroups = mutableListOf<ResolvedGlobalGroup>()
        subsEntries.forEach { (subsItem, rawSubs) ->
            // global scope
            val subGlobalSubsConfigs = globalSubsConfigs.filter { c -> c.subsId == subsItem.id }
            val subGlobalGroupToRules =
                mutableMapOf<RawSubscription.RawGlobalGroup, List<GlobalRule>>()
            rawSubs.globalGroups.filter { g ->
                (subGlobalSubsConfigs.find { c -> c.groupKey == g.key }?.enable
                    ?: g.enable ?: true) && g.valid
            }.forEach { groupRaw ->
                val config = subGlobalSubsConfigs.find { c -> c.groupKey == groupRaw.key }
                val g = ResolvedGlobalGroup(
                    group = groupRaw,
                    subscription = rawSubs,
                    subsItem = subsItem,
                    config = config
                )
                globalGroups.add(g)
                val subRules = groupRaw.rules.map { ruleRaw ->
                    GlobalRule(
                        rule = ruleRaw,
                        g = g,
                        appInfoCache = appInfoCache,
                    )
                }
                subGlobalGroupToRules[groupRaw] = subRules
                globalRules.addAll(subRules)
            }
            subGlobalGroupToRules.values.forEach {
                it.forEach { r ->
                    r.groupToRules = subGlobalGroupToRules
                }
            }
            subGlobalGroupToRules.clear()

            // app scope
            val subAppConfigs = appConfigs.filter { c -> c.subsId == subsItem.id }
            val subGroupSubsConfigs = groupSubsConfigs.filter { c -> c.subsId == subsItem.id }
            val subCategoryConfigs = categoryConfigs.filter { c -> c.subsId == subsItem.id }
            rawSubs.apps.filter { appRaw ->
                // 筛选 当前启用的 app 订阅规则
                appRaw.groups.isNotEmpty() && (subAppConfigs.find { c -> c.appId == appRaw.id }?.enable
                    ?: (appInfoCache[appRaw.id] != null))
            }.forEach { appRaw ->
                val subAppGroups = mutableListOf<RawSubscription.RawAppGroup>()
                val appGroupConfigs = subGroupSubsConfigs.filter { c -> c.appId == appRaw.id }
                val subAppGroupToRules = mutableMapOf<RawSubscription.RawAppGroup, List<AppRule>>()
                val groupAndEnables = appRaw.groups.map { group ->
                    val config = appGroupConfigs.find { c -> c.groupKey == group.key }
                    val category = rawSubs.getCategory(group.name)
                    val categoryConfig =
                        subCategoryConfigs.find { c -> c.categoryKey == category?.key }
                    val enable = getGroupEnable(
                        group,
                        config,
                        category,
                        categoryConfig
                    ) && group.valid
                    ResolvedAppGroup(
                        group = group,
                        subscription = rawSubs,
                        subsItem = subsItem,
                        config = config,
                        app = appRaw,
                        enable = enable,
                    )
                }
                appAllGroups[appRaw.id] = (appAllGroups[appRaw.id] ?: emptyList()) + groupAndEnables
                groupAndEnables.forEach { g ->
                    if (g.enable) {
                        subAppGroups.add(g.group)
                        val subRules = g.group.rules.map { ruleRaw ->
                            AppRule(
                                rule = ruleRaw,
                                g = g,
                                appInfo = appInfoCache[appRaw.id]
                            )
                        }.filter { r -> r.enable }
                        subAppGroupToRules[g.group] = subRules
                        if (subRules.isNotEmpty()) {
                            val rules = appRules[appRaw.id] ?: mutableListOf()
                            appRules[appRaw.id] = rules
                            rules.addAll(subRules)
                        }
                    }
                }
                if (subAppGroups.isNotEmpty()) {
                    appGroups[appRaw.id] = subAppGroups
                }
                subAppGroupToRules.values.forEach {
                    it.forEach { r ->
                        r.groupToRules = subAppGroupToRules
                    }
                }
            }
        }
        RuleSummary(
            globalRules = globalRules,
            globalGroups = globalGroups,
            appIdToRules = appRules,
            appIdToGroups = appGroups,
            appIdToAllGroups = appAllGroups
        )
    }.flowOn(Dispatchers.Default).stateIn(appScope, SharingStarted.Eagerly, RuleSummary())
}

fun getSubsStatus(ruleSummary: RuleSummary, count: Long): String {
    return if (count > 0) {
        "${ruleSummary.numText}/${count}触发"
    } else {
        ruleSummary.numText
    }
}
