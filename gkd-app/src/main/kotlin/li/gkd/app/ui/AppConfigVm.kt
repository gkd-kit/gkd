package li.gkd.app.ui

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import li.gkd.app.data.ActionLog
import li.gkd.app.data.CategoryConfig
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.SubsConfig
import li.gkd.app.db.DbSet
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.ShowGroupState
import li.gkd.app.ui.component.batchUpdateGroupEnable
import li.gkd.app.ui.component.getActualGroupChecked
import li.gkd.app.ui.component.updateRuleGroupEnable
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.Loadable
import li.gkd.app.util.RuleSortOption
import li.gkd.app.util.SubscriptionStore
import li.gkd.app.util.UsedSubsEntry
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.buildSubsEntries
import li.gkd.app.util.buildUsedSubsEntries
import li.gkd.app.util.collator
import li.gkd.app.util.findOption
import li.gkd.app.util.toJson5String

data class AppConfigUiState(
    val globalSubsConfigs: List<SubsConfig>,
    val appSubsConfigs: List<SubsConfig>,
    val categoryConfigs: List<CategoryConfig>,
    val subsPairs: List<Pair<UsedSubsEntry, List<RawSubscription.RawGroupProps>>>,
)

private data class AppConfigDatabaseState(
    val globalSubsConfigs: List<SubsConfig>,
    val appSubsConfigs: List<SubsConfig>,
    val categoryConfigs: List<CategoryConfig>,
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

class AppConfigVm(val route: AppConfigRoute) : BaseViewModel() {
    fun setRuleSortType(option: RuleSortOption) {
        storeFlow.update { it.copy(appRuleSort = option.value) }
    }

    fun toggleShowDisabledRule() {
        storeFlow.update { it.copy(showDisabledRule = !it.showDisabledRule) }
    }

    private val databaseStateFlow = SubscriptionStore.snapshotFlow.flatMapLatest { snapshotState ->
        when (snapshotState) {
            Loadable.Loading -> flowOf(Loadable.Loading)
            is Loadable.Failure -> flowOf(snapshotState)
            is Loadable.Ready -> combine(
                DbSet.subsItemDao.query(),
                DbSet.appConfigDao.queryAppUsedList(route.appId),
            ) { items, appConfigs ->
                val usedSubsIds = items.filter { it.enable }.map { it.id }.sorted()
                val appUsedSubsIds = usedSubsIds.filter { id ->
                    appConfigs.find { it.subsId == id }?.enable != false
                }
                val entries = buildUsedSubsEntries(
                    buildSubsEntries(items, snapshotState.value.subscriptions)
                )
                appUsedSubsIds to entries
            }.distinctUntilChanged().flatMapLatest { (usedSubsIds, entries) ->
                combine(
                    DbSet.subsConfigDao.queryUsedGlobalConfig(),
                    DbSet.subsConfigDao.queryAppConfig(usedSubsIds, route.appId),
                    DbSet.categoryConfigDao.queryBySubsIds(usedSubsIds),
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
                                val checked = getActualGroupChecked(
                                    subs = entry.subscription,
                                    group = group,
                                    appId = route.appId,
                                    subsConfig = subsConfig,
                                    categoryConfig = categoryConfig,
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
            DbSet.actionLogDao.queryLatestByAppId(route.appId)
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
        selectedGroups: Set<ShowGroupState>,
        enabled: Boolean?,
    ): Int {
        return batchUpdateGroupEnable(selectedGroups, enabled).size
    }

    suspend fun buildSelectedGroupsText(selectedGroups: Set<ShowGroupState>): String =
        withContext(Dispatchers.Default) {
            val selectedKeys = selectedGroups.mapTo(mutableSetOf()) {
                Triple(it.subsId, it.groupType, it.groupKey)
            }
            val subsPairs = uiState.value.value?.subsPairs.orEmpty()
            val groups = subsPairs.flatMap { (entry, groups) ->
                groups.filterIsInstance<RawSubscription.RawAppGroup>().filter { group ->
                    Triple(entry.subsItem.id, SubsConfig.AppGroupType, group.key) in selectedKeys
                }
            }
            toJson5String(
                RawSubscription.RawApp(
                    id = route.appId,
                    name = appInfoMapFlow.value[route.appId]?.name,
                    groups = groups,
                )
            )
        }

    suspend fun setGroupEnabled(
        subscription: RawSubscription,
        group: RawSubscription.RawGroupProps,
        subsConfig: SubsConfig?,
        enabled: Boolean,
    ) {
        updateRuleGroupEnable(subscription, route.appId, group, subsConfig, enabled)
    }

    val focusGroupFlow: StateFlow<Triple<Long, String?, Int>?>?
        field = route.focusLog?.let {
            MutableStateFlow<Triple<Long, String?, Int>?>(
                Triple(
                    it.subsId,
                    if (it.groupType == SubsConfig.AppGroupType) it.appId else null,
                    it.groupKey,
                )
            )
        }

    fun consumeFocusGroup() {
        focusGroupFlow?.value = null
    }

}
