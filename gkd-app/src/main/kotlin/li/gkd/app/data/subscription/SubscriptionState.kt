package li.gkd.app.data.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.appScope
import li.gkd.app.a11y.launcherAppIdFlow
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.data.RawSubscription
import li.gkd.app.domain.rule.RuleSummary
import li.gkd.app.domain.rule.RuleSummaryBuilder
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsItem

object SubscriptionState {
    val subsItemsFlow by lazy {
        Db.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
    }

    val subsMapFlow by lazy {
        SubscriptionRepository.snapshotFlow.map { it.value?.subscriptions.orEmpty() }
            .stateIn(
                appScope,
                SharingStarted.Eagerly,
                SubscriptionRepository.snapshotFlow.value.value?.subscriptions.orEmpty(),
            )
    }
    val latestRecordFlow by lazy {
        Db.actionLogDao.queryLatest()
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }
    val latestRecordDescFlow by lazy {
        combine(
            latestRecordFlow,
            subsMapFlow,
            AppInfoRepository.appInfoMapFlow,
        ) { record, subsMap, appMap ->
            if (record == null) return@combine null
            val isAppRule = record.groupType == RuleGroupType.App
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

    fun buildUsedSubsEntries(
        items: List<SubsItem>,
        subscriptions: Map<Long, RawSubscription>,
    ): List<UsedSubsEntry> = items.mapNotNull { item ->
        subscriptions[item.id]?.takeIf { item.enable && it.hasRule }?.let { subscription ->
            UsedSubsEntry(item, subscription)
        }
    }

    val ruleSummaryFlow by lazy {
        combine(
            subsMapFlow,
            AppInfoRepository.appInfoMapFlow,
            Db.subscriptionConfigStore.observe(),
            launcherAppIdFlow,
        ) { subscriptions, appInfoCache, configs, launcherAppId ->
            RuleSummaryBuilder.build(
                subscriptions = buildUsedSubsEntries(configs.subsItems, subscriptions),
                appInfoById = appInfoCache,
                appConfigs = configs.appConfigs,
                groupConfigs = configs.appGroupConfigs + configs.globalGroupConfigs,
                categoryConfigs = configs.categoryConfigs,
                launcherAppId = launcherAppId,
            )
        }.flowOn(Dispatchers.Default).stateIn(appScope, SharingStarted.Eagerly, RuleSummary())
    }
}
