package li.songe.gkd.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.getActualGroupChecked
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.asMutableStateFlow
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.UsedSubsEntry
import li.songe.gkd.util.collator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.usedSubsEntriesFlow


class AppConfigVm(val route: AppConfigRoute) : BaseViewModel() {
    val ruleSortTypeFlow = storeFlow.mapNew {
        RuleSortOption.objects.findOption(it.appRuleSort)
    }
    val showDisabledRuleFlow = storeFlow.asMutableStateFlow(
        getter = { it.showDisabledRule },
        setter = {
            storeFlow.value.copy(showDisabledRule = it)
        }
    )

    private val usedSubsIdsFlow = subsItemsFlow.mapNew { list ->
        list.filter { it.enable }.map { it.id }.sorted()
    }

    private val appConfigsFlow = DbSet.appConfigDao.queryAppUsedList(route.appId).attachLoad()

    private val appUsedSubsIdsFlow = combine(usedSubsIdsFlow, appConfigsFlow) { ids, configs ->
        ids.filter {
            configs.find { c -> c.subsId == it }?.enable != false
        }
    }.stateInit(usedSubsIdsFlow.value)

    private val latestLogsFlow = ruleSortTypeFlow.map {
        if (it == RuleSortOption.ByActionTime) {
            DbSet.actionLogDao.queryLatestByAppId(route.appId)
        } else {
            flowOf(emptyList())
        }
    }.flattenConcat().attachLoad().stateInit(emptyList())

    val globalSubsConfigsFlow = DbSet.subsConfigDao.queryUsedGlobalConfig().attachLoad()
        .stateInit(emptyList())

    val appSubsConfigsFlow = appUsedSubsIdsFlow.map {
        DbSet.subsConfigDao.queryAppConfig(it, route.appId)
    }.flattenConcat().attachLoad()
        .stateInit(emptyList())

    val categoryConfigsFlow = appUsedSubsIdsFlow.map {
        DbSet.categoryConfigDao.queryBySubsIds(it)
    }.flattenConcat().attachLoad()
        .stateInit(emptyList())

    private val temp1ListFlow = combine(
        appUsedSubsIdsFlow,
        usedSubsEntriesFlow,
    ) { usedSubsIds, list ->
        list.map { e ->
            val globalGroups = e.subscription.globalGroups
            val appGroups = if (usedSubsIds.contains(e.subsItem.id)) {
                e.subscription.getAppGroups(route.appId)
            } else {
                emptyList()
            }
            e to (globalGroups + appGroups)
        }.filter { it.second.isNotEmpty() }
    }.stateInit(emptyList())

    private val checkedGroupSetFlow = combine(
        temp1ListFlow,
        globalSubsConfigsFlow,
        categoryConfigsFlow,
        appSubsConfigsFlow,
    ) { list, globalSubsConfigs, categoryConfigs, appSubsConfigs ->
        val checkedSet = mutableSetOf<Triple<Long, Int, Int>>()
        list.forEach { (entry, groups) ->
            groups.forEach { group ->
                val subsConfig = when (group) {
                    is RawSubscription.RawAppGroup -> appSubsConfigs
                    is RawSubscription.RawGlobalGroup -> globalSubsConfigs
                }.find { it.subsId == entry.subsItem.id && it.groupKey == group.key }
                val category = when (group) {
                    is RawSubscription.RawAppGroup -> entry.subscription.getCategory(group.name)
                    is RawSubscription.RawGlobalGroup -> null
                }
                val categoryConfig = if (category != null) {
                    categoryConfigs.find { it.subsId == entry.subsItem.id && it.categoryKey == category.key }
                } else {
                    null
                }
                val checked = getActualGroupChecked(
                    subs = entry.subscription,
                    group = group,
                    appId = route.appId,
                    subsConfig = subsConfig,
                    categoryConfig = categoryConfig,
                ) && (group !is RawSubscription.RawGlobalGroup || subsConfig?.enable != false)
                if (checked) {
                    checkedSet.add(Triple(entry.subsItem.id, group.groupType, group.key))
                }
            }
        }
        checkedSet
    }.stateInit(emptySet())

    private val actualCheckedGroupSetFlow =
        MutableStateFlow<Set<Triple<Long, Int, Int>>>(emptySet())

    init {
        showDisabledRuleFlow.launchOnChange {
            actualCheckedGroupSetFlow.value = checkedGroupSetFlow.value
        }
        viewModelScope.launch {
            combine(checkedGroupSetFlow, firstLoadingFlow) { a, first -> first to a }
                .collect { (first, a) ->
                    if (!first) {
                        actualCheckedGroupSetFlow.update { b -> a + b }
                    }
                }
        }
    }

    private val temp2ListFlow = combine(
        temp1ListFlow,
        showDisabledRuleFlow,
        actualCheckedGroupSetFlow,
    ) { list, showDisabledRule, checkedSet ->
        if (showDisabledRule) {
            list
        } else {
            val newList = mutableListOf<Pair<UsedSubsEntry, List<RawSubscription.RawGroupProps>>>()
            list.forEach { (entry, groups) ->
                val newGroups = groups.filter { g ->
                    checkedSet.contains(Triple(entry.subsItem.id, g.groupType, g.key))
                }
                if (newGroups.isNotEmpty()) {
                    newList.add(entry to newGroups)
                }
            }
            newList
        }
    }.stateInit(emptyList())

    val subsPairsFlow = combine(
        temp2ListFlow,
        latestLogsFlow,
        ruleSortTypeFlow
    ) { list, logs, sortType ->
        when (sortType) {
            RuleSortOption.ByDefault -> list
            RuleSortOption.ByRuleName -> list.map { e ->
                e.first to e.second.sortedWith { a, b ->
                    collator.compare(
                        a.name,
                        b.name
                    )
                }
            }

            RuleSortOption.ByActionTime -> list.map { e ->
                e.first to e.second.sortedBy { a ->
                    -(logs.find { c ->
                        c.subsId == e.first.subsItem.id && c.groupType == a.groupType && c.groupKey == a.key
                    }?.id ?: 0)
                }
            }
        }
    }.combine(firstLoadingFlow) { list, firstLoading ->
        if (firstLoading) {
            emptyList()
        } else {
            list
        }
    }.stateInit(emptyList())

    val groupSizeFlow = subsPairsFlow.mapNew { list ->
        list.sumOf { it.second.size }
    }

    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())

    private fun getAllSelectedDataSet() = subsPairsFlow.value.flatMap { e ->
        e.second.map { g ->
            g.toGroupState(subsId = e.first.subsItem.id, appId = route.appId)
        }
    }.toSet()

    fun selectAll() {
        selectedDataSetFlow.value = getAllSelectedDataSet()
    }

    fun invertSelect() {
        selectedDataSetFlow.value = getAllSelectedDataSet() - selectedDataSetFlow.value
    }

    val focusGroupFlow = route.focusLog?.let {
        MutableStateFlow<Triple<Long, String?, Int>?>(
            Triple(
                it.subsId,
                if (it.groupType == SubsConfig.AppGroupType) it.appId else null,
                it.groupKey,
            )
        )
    }

    init {
        viewModelScope.launch {
            appUsedSubsIdsFlow.collect {
                LogUtils.d(it)
            }
        }
    }

}
