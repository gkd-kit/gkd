package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.AppConfigPageDestination
import li.songe.gkd.util.ResolvedAppGroup
import li.songe.gkd.util.ResolvedGlobalGroup
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.collator
import li.songe.gkd.util.getGroupRawEnable
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import javax.inject.Inject

@HiltViewModel
class AppConfigVm @Inject constructor(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = AppConfigPageDestination.argsFrom(stateHandle)

    val innerDisabledDlgFlow = MutableStateFlow(false)

    private val latestGlobalLogsFlow = DbSet.clickLogDao.queryAppLatest(
        args.appId,
        SubsConfig.GlobalGroupType
    )

    private val latestAppLogsFlow = DbSet.clickLogDao.queryAppLatest(
        args.appId,
        SubsConfig.AppGroupType
    )

    val ruleSortTypeFlow = MutableStateFlow<RuleSortOption>(RuleSortOption.Default)

    private val subsFlow = combine(subsIdToRawFlow, subsItemsFlow) { subsIdToRaw, subsItems ->
        subsItems.mapNotNull { if (it.enable && subsIdToRaw[it.id] != null) it to subsIdToRaw[it.id]!! else null }
    }
    private val rawGlobalGroups = subsFlow.map {
        it.map { (subsItem, subscription) ->
            subscription.globalGroups.map { g ->
                ResolvedGlobalGroup(
                    group = g,
                    subsItem = subsItem,
                    subscription = subscription,
                    // secondary assignment
                    config = null,
                )
            }
        }.flatten()
    }
    private val sortedGlobalGroupsFlow = combine(
        rawGlobalGroups,
        ruleSortTypeFlow,
        latestGlobalLogsFlow
    ) { list, type, logs ->
        when (type) {
            RuleSortOption.Default -> list
            RuleSortOption.ByName -> list.sortedWith { a, b ->
                collator.compare(
                    a.group.name,
                    b.group.name
                )
            }

            RuleSortOption.ByTime -> list.sortedBy { a ->
                -(logs.find { c -> c.groupKey == a.group.key && c.subsId == a.subsItem.id }?.id
                    ?: 0)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val globalConfigs = subsFlow.map { subs ->
        DbSet.subsConfigDao.queryGlobalConfig(subs.map { it.first.id })
    }.flatMapLatest { it }
    val globalGroupsFlow = combine(sortedGlobalGroupsFlow, globalConfigs) { groups, configs ->
        groups.map { g ->
            ResolvedGlobalGroup(
                group = g.group,
                subsItem = g.subsItem,
                subscription = g.subscription,
                config = configs.find { c -> c.subsItemId == g.subsItem.id && c.groupKey == g.group.key },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val unsortedAppGroupsFlow = subsFlow.map {
        it.mapNotNull { s ->
            s.second.apps.find { a -> a.id == args.appId }?.let { app ->
                app.groups.map { g ->
                    ResolvedAppGroup(
                        group = g,
                        subsItem = s.first,
                        subscription = s.second,
                        app = app,
                        // secondary assignment
                        config = null,
                        enable = false
                    )
                }
            }
        }.flatten()
    }
    private val sortedAppGroupsFlow = combine(
        unsortedAppGroupsFlow,
        ruleSortTypeFlow,
        latestAppLogsFlow
    ) { list, type, logs ->
        when (type) {
            RuleSortOption.Default -> list
            RuleSortOption.ByName -> list.sortedWith { a, b ->
                collator.compare(
                    a.group.name,
                    b.group.name
                )
            }

            RuleSortOption.ByTime -> list.sortedBy { a ->
                -(logs.find { c -> c.groupKey == a.group.key && c.subsId == a.subsItem.id }?.id
                    ?: 0)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val appConfigsFlow = subsFlow.map { subs ->
        DbSet.subsConfigDao.queryAppConfig(subs.map { it.first.id }, args.appId)
    }.flatMapLatest { it }
    private val categoryConfigsFlow = subsFlow.map { subs ->
        DbSet.categoryConfigDao.queryBySubsIds(subs.map { it.first.id })
    }.flatMapLatest { it }
    val appGroupsFlow = combine(
        sortedAppGroupsFlow,
        appConfigsFlow,
        categoryConfigsFlow
    ) { groups, configs, categoryConfigs ->
        groups.map { g ->
            val config =
                configs.find { c -> c.subsItemId == g.subsItem.id && c.groupKey == g.group.key }
            val enable = g.group.valid && getGroupRawEnable(
                g.group,
                config,
                g.subscription.groupToCategoryMap[g.group],
                categoryConfigs.find { c -> c.subsItemId == g.subsItem.id && c.categoryKey == g.subscription.groupToCategoryMap[g.group]?.key }
            )
            ResolvedAppGroup(
                group = g.group,
                subsItem = g.subsItem,
                subscription = g.subscription,
                app = g.app,
                config = config,
                enable = enable
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}

