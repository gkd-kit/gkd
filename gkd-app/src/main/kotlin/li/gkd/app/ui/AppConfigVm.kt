package li.gkd.app.ui

import li.gkd.app.domain.rule.toRuleGroupTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.subscription.UsedSubsEntry
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.a11y.launcherAppId
import li.gkd.app.util.MutexState
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.util.RuleSortOption
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.util.collator
import li.gkd.app.util.findOption
import li.gkd.app.util.toJson5String
import li.gkd.db.ActionLog
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.Db
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.RuleGroupType

data class AppConfigUiState(
    val globalSubsConfigs: List<SubsGlobalGroupConfig>,
    val appSubsConfigs: List<SubsAppGroupConfig>,
    val categoryConfigs: List<SubsCategoryConfig>,
    val subsPairs: List<Pair<UsedSubsEntry, List<RawSubscription.RawGroupProps>>>,
)

private data class AppConfigDatabaseState(
    val globalSubsConfigs: List<SubsGlobalGroupConfig>,
    val appSubsConfigs: List<SubsAppGroupConfig>,
    val categoryConfigs: List<SubsCategoryConfig>,
    val subsPairs: List<Pair<UsedSubsEntry, List<RawSubscription.RawGroupProps>>>,
    val checkedGroupKeys: Set<Triple<Long, Int, Int>>,
)

private data class AppConfigVisibilityState(
    val database: AppConfigDatabaseState,
    val showDisabled: Boolean,
    val visibleCheckedGroupKeys: Set<Triple<Long, Int, Int>>,
)

private data class AppConfigSortState(
    val option: RuleSortOption,
    val latestLogs: List<ActionLog>,
)

class AppConfigVm(
    val route: AppConfigRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    fun setRuleSortType(option: RuleSortOption) {
        AppStore.updateSettings { it.copy(appRuleSort = option.value) }
    }

    fun toggleShowDisabledRule() {
        AppStore.updateSettings { it.copy(showDisabledRule = !it.showDisabledRule) }
    }

    private val databaseStateFlow = SubscriptionRepository.snapshotFlow.flatMapLatest { snapshotState ->
        when (snapshotState) {
            Loadable.Loading -> flowOf(Loadable.Loading)
            is Loadable.Failure -> flowOf(snapshotState)
            is Loadable.Ready -> combine(
                Db.subsItemDao.query(),
                Db.subsAppConfigDao.queryAppUsedList(route.appId),
            ) { items, appConfigs ->
                val usedSubsIds = items.filter { it.enable }.map { it.id }.sorted()
                val appUsedSubsIds = usedSubsIds.filter { id ->
                    appConfigs.find { it.subsId == id }?.enable != false
                }
                val entries = SubscriptionState.buildUsedSubsEntries(
                    items,
                    snapshotState.value.subscriptions,
                )
                appUsedSubsIds to entries
            }.distinctUntilChanged().flatMapLatest { (usedSubsIds, entries) ->
                combine(
                    Db.subsGlobalGroupConfigDao.queryUsedList(),
                    Db.subsAppGroupConfigDao.queryAppConfig(usedSubsIds, route.appId),
                    Db.subsCategoryConfigDao.queryBySubsIds(usedSubsIds),
                ) { globalConfigs, appConfigs, categoryConfigs ->
                    val subsPairs = entries.map { entry ->
                        val globalGroups = entry.subscription.globalGroups.filter { group ->
                            globalConfigs.find {
                                it.subsId == entry.subsItem.id && it.groupKey == group.key
                            }?.enable != false
                        }
                        val appGroups = if (entry.subsItem.id in usedSubsIds) {
                            entry.subscription.getAppGroups(route.appId)
                        } else {
                            emptyList()
                        }
                        entry to (globalGroups + appGroups)
                    }.filter { it.second.isNotEmpty() }

                    val checkedGroupKeys = buildSet {
                        subsPairs.forEach { (entry, groups) ->
                            groups.forEach { group ->
                                val subsConfig = when (group) {
                                    is RawSubscription.RawAppGroup -> appConfigs
                                    is RawSubscription.RawGlobalGroup -> globalConfigs
                                }.find {
                                    it.subsId == entry.subsItem.id && it.groupKey == group.key
                                }
                                val category = when (group) {
                                    is RawSubscription.RawAppGroup ->
                                        entry.subscription.getCategory(group.name)

                                    is RawSubscription.RawGlobalGroup -> null
                                }
                                val categoryConfig = category?.let { targetCategory ->
                                    categoryConfigs.find {
                                        it.subsId == entry.subsItem.id &&
                                            it.categoryKey == targetCategory.key
                                    }
                                }
                                val checked = RuleGroupPolicy.getActualGroupChecked(
                                    subscription = entry.subscription,
                                    group = group,
                                    appId = route.appId,
                                    subsConfig = subsConfig,
                                    categoryConfig = categoryConfig,
                                    launcherAppId = launcherAppId,
                                    systemAppIds = AppInfoRepository.systemAppsFlow.value,
                                ) && (
                                    group !is RawSubscription.RawGlobalGroup ||
                                        subsConfig?.enable != false
                                    )
                                if (checked) {
                                    add(Triple(entry.subsItem.id, group.groupType, group.key))
                                }
                            }
                        }
                    }
                    AppConfigDatabaseState(
                        globalSubsConfigs = globalConfigs,
                        appSubsConfigs = appConfigs,
                        categoryConfigs = categoryConfigs,
                        subsPairs = subsPairs,
                        checkedGroupKeys = checkedGroupKeys,
                    )
                }
            }.map<AppConfigDatabaseState, Loadable<AppConfigDatabaseState>> {
                Loadable.Ready(it)
            }.catch { emit(Loadable.Failure(it)) }
        }
    }

    private val visibilityStateFlow = combine(
        databaseStateFlow,
        storeFlow,
    ) { database, store -> database to store.showDisabledRule }
        .runningFold(Loadable.Loading as Loadable<AppConfigVisibilityState>) {
            previous, (databaseState, showDisabled) ->
            when (databaseState) {
                Loadable.Loading -> Loadable.Loading
                is Loadable.Failure -> databaseState
                is Loadable.Ready -> {
                    val database = databaseState.value
                    val previousState = previous.value
                    Loadable.Ready(
                        AppConfigVisibilityState(
                            database = database,
                            showDisabled = showDisabled,
                            visibleCheckedGroupKeys = if (
                                previousState == null || previousState.showDisabled != showDisabled
                            ) {
                                database.checkedGroupKeys
                            } else {
                                previousState.visibleCheckedGroupKeys + database.checkedGroupKeys
                            },
                        )
                    )
                }
            }
        }

    private val sortStateFlow = storeFlow.flatMapLatest { store ->
        val option = RuleSortOption.objects.findOption(store.appRuleSort)
        val logsFlow = if (option == RuleSortOption.ByActionTime) {
            Db.actionLogDao.queryLatestByAppId(route.appId)
        } else {
            flowOf(emptyList())
        }
        logsFlow.map { AppConfigSortState(option, it) }
    }

    private val sortStateLoadableFlow = sortStateFlow
        .map<AppConfigSortState, Loadable<AppConfigSortState>> { Loadable.Ready(it) }
        .catch { emit(Loadable.Failure(it)) }

    val uiState: StateFlow<Loadable<AppConfigUiState>> = combine(
        visibilityStateFlow,
        sortStateLoadableFlow,
    ) { visibilityState, sortState ->
        if (visibilityState is Loadable.Failure) return@combine visibilityState
        if (sortState is Loadable.Failure) return@combine sortState
        val visibility = visibilityState.value ?: return@combine Loadable.Loading
        val sort = sortState.value ?: return@combine Loadable.Loading
        val database = visibility.database
        val visiblePairs = if (visibility.showDisabled) {
            database.subsPairs
        } else {
            database.subsPairs.mapNotNull { (entry, groups) ->
                val visibleGroups = groups.filter { group ->
                    Triple(entry.subsItem.id, group.groupType, group.key) in
                        visibility.visibleCheckedGroupKeys
                }
                (entry to visibleGroups).takeIf { visibleGroups.isNotEmpty() }
            }
        }
        val sortedPairs = when (sort.option) {
            RuleSortOption.ByDefault -> visiblePairs
            RuleSortOption.ByRuleName -> visiblePairs.map { entry ->
                entry.first to entry.second.sortedWith { a, b ->
                    collator.compare(a.name, b.name)
                }
            }

            RuleSortOption.ByActionTime -> visiblePairs.map { entry ->
                entry.first to entry.second.sortedBy { group ->
                    -(sort.latestLogs.find {
                        it.subsId == entry.first.subsItem.id &&
                            it.groupType == group.groupType &&
                            it.groupKey == group.key
                    }?.id ?: 0)
                }
            }
        }
        Loadable.Ready(
            AppConfigUiState(
                globalSubsConfigs = database.globalSubsConfigs,
                appSubsConfigs = database.appSubsConfigs,
                categoryConfigs = database.categoryConfigs,
                subsPairs = sortedPairs,
            )
        )
    }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    suspend fun updateSelectedEnabled(
        selectedGroups: Set<RuleGroupTarget>,
        enabled: Boolean?,
    ): Int {
        return RuleGroupConfigService.batchUpdateGroupEnabled(
            selectedGroups,
            enabled,
            launcherAppId,
            AppInfoRepository.systemAppsFlow.value,
        ).size
    }

    suspend fun buildSelectedGroupsText(selectedGroups: Set<RuleGroupTarget>): String =
        withContext(Dispatchers.Default) {
            val selectedKeys = selectedGroups.mapTo(mutableSetOf()) {
                Triple(it.subsId, it.groupType, it.groupKey)
            }
            val subsPairs = uiState.value.value?.subsPairs.orEmpty()
            val groups = subsPairs.flatMap { (entry, groups) ->
                groups.filterIsInstance<RawSubscription.RawAppGroup>().filter { group ->
                    Triple(entry.subsItem.id, RuleGroupType.App, group.key) in selectedKeys
                }
            }
            check(groups.isNotEmpty()) { "所选规则已变化，无可复制的应用规则" }
            toJson5String(
                RawSubscription.RawApp(
                    id = route.appId,
                    name = AppInfoRepository.appInfoMapFlow.value[route.appId]?.name,
                    groups = groups,
                )
            )
        }

    suspend fun setGroupEnabled(
        subscription: RawSubscription,
        group: RawSubscription.RawGroupProps,
        enabled: Boolean,
    ) {
        RuleGroupConfigService.updateGroupEnabled(
            group.toRuleGroupTarget(subscription.id, route.appId),
            enabled,
        )
    }

    val focusGroupFlow: StateFlow<Triple<Long, String?, Int>?>?
        field = route.focusLog?.let {
            MutableStateFlow<Triple<Long, String?, Int>?>(
                Triple(
                    it.subsId,
                    if (it.groupType == RuleGroupType.App) it.appId else null,
                    it.groupKey,
                )
            )
        }

    fun consumeFocusGroup() {
        focusGroupFlow?.value = null
    }

}
