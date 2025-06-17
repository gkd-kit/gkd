package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.generated.destinations.AppConfigPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.util.LinkLoad
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.ViewModelExt
import li.songe.gkd.util.collator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.usedSubsEntriesFlow


class AppConfigVm(stateHandle: SavedStateHandle) : ViewModelExt() {
    private val args = AppConfigPageDestination.argsFrom(stateHandle)

    val linkLoad = LinkLoad(viewModelScope)

    val ruleSortTypeFlow = storeFlow.map(viewModelScope) {
        RuleSortOption.allSubObject.findOption(it.appRuleSortType)
    }

    val appShowInnerDisableFlow = storeFlow.map(viewModelScope) {
        it.appShowInnerDisable
    }

    private val usedSubsIdsFlow = subsItemsFlow.map(viewModelScope) { list ->
        list.filter { it.enable }.map { it.id }.sorted()
    }

    private val appConfigsFlow = DbSet.appConfigDao.queryAppUsedList(args.appId)
        .let(linkLoad::invoke)

    private val appUsedSubsIdsFlow = combine(usedSubsIdsFlow, appConfigsFlow) { ids, configs ->
        ids.filter {
            configs.find { c -> c.subsId == it }?.enable != false
        }
    }.stateInit(usedSubsIdsFlow.value)

    private val latestLogsFlow = ruleSortTypeFlow.map {
        if (it == RuleSortOption.ByTime) {
            DbSet.actionLogDao.queryLatestByAppId(args.appId)
        } else {
            flowOf(emptyList())
        }
    }.flattenConcat().let(linkLoad::invoke).stateInit(emptyList())

    val globalSubsConfigsFlow = DbSet.subsConfigDao.queryUsedGlobalConfig().let(linkLoad::invoke)
        .stateInit(emptyList())

    val appSubsConfigsFlow = appUsedSubsIdsFlow.map {
        DbSet.subsConfigDao.queryAppConfig(it, args.appId)
    }.flattenConcat().let(linkLoad::invoke)
        .stateInit(emptyList())

    val categoryConfigsFlow = appUsedSubsIdsFlow.map {
        DbSet.categoryConfigDao.queryBySubsIds(it)
    }.flattenConcat().let(linkLoad::invoke)
        .stateInit(emptyList())

    private val temp1ListFlow = combine(
        appUsedSubsIdsFlow,
        usedSubsEntriesFlow,
        appShowInnerDisableFlow,
        globalSubsConfigsFlow,
    ) { usedSubsIds, list, show, configs ->
        list.map { e ->
            val globalGroups = e.subscription.globalGroups
                .filter { g -> configs.find { it.subsId == e.subsItem.id && it.groupKey == g.key }?.enable != false }
                .let {
                    if (show) {
                        it
                    } else {
                        it.filter { g ->
                            !e.subscription.getGlobalGroupInnerDisabled(
                                g,
                                args.appId
                            )
                        }
                    }
                }
            val appGroups = if (usedSubsIds.contains(e.subsItem.id)) {
                e.subscription.getAppGroups(args.appId)
            } else {
                emptyList()
            }
            e to (globalGroups + appGroups)
        }.filter { it.second.isNotEmpty() }
    }.stateInit(emptyList())

    val subsPairsFlow = combine(
        temp1ListFlow,
        latestLogsFlow,
        ruleSortTypeFlow
    ) { list, logs, sortType ->
        when (sortType) {
            RuleSortOption.Default -> list
            RuleSortOption.ByName -> list.map { e ->
                e.first to e.second.sortedWith { a, b ->
                    collator.compare(
                        a.name,
                        b.name
                    )
                }
            }

            RuleSortOption.ByTime -> list.map { e ->
                e.first to e.second.sortedBy { a ->
                    -(logs.find { c ->
                        c.subsId == e.first.subsItem.id && c.groupType == a.groupType && c.groupKey == a.key
                    }?.id ?: 0)
                }
            }
        }
    }.combine(linkLoad.firstLoadingFlow) { list, firstLoading ->
        if (firstLoading) {
            emptyList()
        } else {
            list
        }
    }.stateInit(emptyList())

    val groupSizeFlow = subsPairsFlow.map(viewModelScope) { list ->
        list.sumOf { it.second.size }
    }

    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())

    private fun getAllSelectedDataSet() = subsPairsFlow.value.map { e ->
        e.second.map { g ->
            g.toGroupState(subsId = e.first.subsItem.id, appId = args.appId)
        }
    }.flatten().toSet()

    fun selectAll() {
        selectedDataSetFlow.value = getAllSelectedDataSet()
    }

    fun invertSelect() {
        selectedDataSetFlow.value = getAllSelectedDataSet() - selectedDataSetFlow.value
    }

    val focusGroupFlow = args.focusLog?.let {
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

