package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.AppConfigPageDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.ResolvedAppGroup
import li.songe.gkd.data.ResolvedGlobalGroup
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LinkLoad
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.collator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getGroupEnable
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.usedSubsEntriesFlow


class AppConfigVm(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = AppConfigPageDestination.argsFrom(stateHandle)

    val linkLoad = LinkLoad(viewModelScope)

    private val latestGlobalLogsFlow = DbSet.actionLogDao.queryAppLatest(
        args.appId,
        SubsConfig.GlobalGroupType
    ).let(linkLoad::invoke)

    private val latestAppLogsFlow = DbSet.actionLogDao.queryAppLatest(
        args.appId,
        SubsConfig.AppGroupType
    ).let(linkLoad::invoke)

    val ruleSortTypeFlow =
        storeFlow.map(viewModelScope) { RuleSortOption.allSubObject.findOption(it.appRuleSortType) }

    private val rawGlobalGroups = usedSubsEntriesFlow.map {
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

    private val globalConfigs = usedSubsEntriesFlow.map { subs ->
        DbSet.subsConfigDao.queryGlobalConfig(subs.map { it.subsItem.id })
    }.flatMapLatest { it }.let(linkLoad::invoke)
    val globalGroupsFlow = combine(sortedGlobalGroupsFlow, globalConfigs) { groups, configs ->
        groups.mapNotNull { g ->
            val config =
                configs.find { c -> c.subsItemId == g.subsItem.id && c.groupKey == g.group.key }
            if (config?.enable == false) {
                null
            } else {
                ResolvedGlobalGroup(
                    group = g.group,
                    subsItem = g.subsItem,
                    subscription = g.subscription,
                    config = config,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val unsortedAppGroupsFlow = usedSubsEntriesFlow.map {
        it.mapNotNull { s ->
            s.subscription.apps.find { a -> a.id == args.appId }?.let { app ->
                app.groups.map { g ->
                    ResolvedAppGroup(
                        group = g,
                        subsItem = s.subsItem,
                        subscription = s.subscription,
                        app = app,

                        // secondary assignment
                        config = null,
                        enable = false,
                        category = null,
                        categoryConfig = null,
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

    private val appConfigsFlow = usedSubsEntriesFlow.map { subs ->
        DbSet.subsConfigDao.queryAppConfig(subs.map { it.subsItem.id }, args.appId)
    }.flatMapLatest { it }.let(linkLoad::invoke)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val categoryConfigsFlow = usedSubsEntriesFlow.map { subs ->
        DbSet.categoryConfigDao.queryBySubsIds(subs.map { it.subsItem.id })
    }.flatMapLatest { it }.let(linkLoad::invoke)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val appGroupsFlow = combine(
        sortedAppGroupsFlow,
        appConfigsFlow,
        categoryConfigsFlow
    ) { groups, configs, categoryConfigs ->
        groups.map { g ->
            val config =
                configs.find { c -> c.subsItemId == g.subsItem.id && c.groupKey == g.group.key }
            val category = g.subscription.groupToCategoryMap[g.group]
            val categoryConfig =
                categoryConfigs.find { c -> c.subsItemId == g.subsItem.id && c.categoryKey == g.subscription.groupToCategoryMap[g.group]?.key }
            val enable = g.group.valid && getGroupEnable(
                g.group,
                config,
                category,
                categoryConfig,
            )
            ResolvedAppGroup(
                group = g.group,
                subsItem = g.subsItem,
                subscription = g.subscription,
                app = g.app,
                config = config,
                enable = enable,
                category = category,
                categoryConfig = categoryConfig,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}

