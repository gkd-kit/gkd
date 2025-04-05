package li.songe.gkd.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.GlobalGroupExcludePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.getGlobalGroupChecked
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.getGroupEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

data class ShowGroupState(
    val subsId: Long,
    val appId: String? = null,
    val groupKey: Int? = null,
    val pageAppId: String? = null,
    val addAppRule: Boolean = false,
) {
    val groupType: Int
        get() = if (appId != null) {
            SubsConfig.AppGroupType
        } else {
            SubsConfig.GlobalGroupType
        }

    suspend fun querySubsConfig(): SubsConfig? {
        groupKey ?: error("require groupKey")
        return if (groupType == SubsConfig.AppGroupType) {
            appId ?: error("require appId")
            DbSet.subsConfigDao.queryAppGroupTypeConfig(subsId, appId, groupKey).first()
        } else {
            DbSet.subsConfigDao.queryGlobalGroupTypeConfig(subsId, groupKey).first()
        }
    }

    suspend fun queryCategoryConfig(): CategoryConfig? {
        groupKey ?: error("require groupKey")
        val subs = subsIdToRawFlow.value[subsId] ?: error("require subs")
        val group = if (groupType == SubsConfig.AppGroupType) {
            subs.apps.find { it.id == appId }?.groups
        } else {
            subs.globalGroups
        }?.find { it.key == groupKey } ?: error("require group")
        val category = subs.groupToCategoryMap[group] ?: return null
        return DbSet.categoryConfigDao.queryCategoryConfig(subsId, category.key)
    }
}

fun RawSubscription.RawGroupProps.toGroupState(
    subsId: Long,
    appId: String? = null,
) = when (this) {
    is RawSubscription.RawAppGroup -> ShowGroupState(
        subsId = subsId,
        appId = appId ?: error("require appId"),
        groupKey = key,
        pageAppId = appId,
    )

    is RawSubscription.RawGlobalGroup -> ShowGroupState(
        subsId = subsId,
        groupKey = key,
        pageAppId = appId,
    )
}

suspend fun batchUpdateGroupEnable(
    groups: Collection<ShowGroupState>,
    enable: Boolean?
): List<Pair<ShowGroupState, SubsConfig>> {
    val diffDataList = groups.map { g ->
        if (g.groupKey == null) return@map null
        val subscription = subsIdToRawFlow.value[g.subsId] ?: return@map null
        val targetGroup = subscription.run {
            if (g.appId != null) {
                apps.find { a -> a.id == g.appId }?.groups?.find { it.key == g.groupKey }
            } else {
                globalGroups.find { it.key == g.groupKey }
            }
        }
        if (targetGroup?.valid != true) {
            return@map null
        }
        val subsConfig = g.querySubsConfig()
        val categoryConfig = g.queryCategoryConfig()
        if (enable == null && subsConfig?.enable == null && subsConfig?.exclude.isNullOrEmpty()) {
            return@map null
        }
        val newSubsConfig = if (g.appId != null) {
            targetGroup as RawSubscription.RawAppGroup
            val oldEnable = getGroupEnable(
                targetGroup,
                subsConfig,
                subscription.groupToCategoryMap[targetGroup],
                categoryConfig
            )
            // app rule
            val newSubsConfig = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                type = SubsConfig.AppGroupType,
                subsId = g.subsId,
                appId = g.appId,
                groupKey = g.groupKey,
                enable = enable
            ))
            val newEnable = getGroupEnable(
                targetGroup,
                newSubsConfig,
                subscription.groupToCategoryMap[targetGroup],
                categoryConfig
            )
            if (enable == newEnable && oldEnable == newEnable) {
                return@map null
            }
            newSubsConfig
        } else {
            // global rule
            if (g.pageAppId != null) {
                // global rule for some app
                targetGroup as RawSubscription.RawGlobalGroup
                val excludeData = ExcludeData.parse(subsConfig?.exclude)
                getGlobalGroupChecked(subscription, excludeData, targetGroup, g.pageAppId).let {
                    if (it == null) return@map null
                }
                (subsConfig ?: SubsConfig(
                    type = SubsConfig.GlobalGroupType,
                    subsId = g.subsId,
                    groupKey = g.groupKey,
                )).copy(
                    exclude = excludeData.copy(
                        appIds = excludeData.appIds.toMutableMap().apply {
                            if (enable != null) {
                                if (!contains(g.pageAppId) && enable) {
                                    return@map null
                                }
                                set(g.pageAppId, !enable)
                            } else {
                                if (!contains(g.pageAppId)) {
                                    return@map null
                                }
                                remove(g.pageAppId)
                            }
                        }
                    ).stringify()
                )
            } else {
                // full global rule
                val newSubsConfig = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                    type = SubsConfig.GlobalGroupType,
                    subsId = g.subsId,
                    groupKey = g.groupKey,
                    enable = enable
                ))
                val oldEnable = getGroupEnable(
                    targetGroup,
                    subsConfig,
                )
                val newEnable = getGroupEnable(targetGroup, newSubsConfig)
                if (enable == newEnable && oldEnable == newEnable) {
                    return@map null
                }
                newSubsConfig
            }
        }

        if (subsConfig != newSubsConfig) {
            g to newSubsConfig
        } else {
            null
        }
    }.filterNotNull()
    val newSubsConfigs = diffDataList.map { it.second }
    val canDeleteList = newSubsConfigs.filter {
        it.type == SubsConfig.AppGroupType && it.enable == null && it.exclude.isEmpty()
    }
    DbSet.subsConfigDao.insertAndDelete(
        newSubsConfigs.filterNot { canDeleteList.contains(it) },
        canDeleteList
    )
    return diffDataList
}

@Composable
private fun useEditSubsApp(
    subs: RawSubscription?,
    appId: String?,
): RawSubscription.RawApp? {
    return remember(subs, appId) {
        if (subs != null && appId != null) {
            subs.apps.find { a -> a.id == appId } ?: RawSubscription.RawApp(
                id = appId,
                name = appInfoCacheFlow.value[appId]?.name
            )
        } else {
            null
        }
    }
}

class RuleGroupState(
    private val vm: MainViewModel,
) {
    val showGroupFlow = MutableStateFlow<ShowGroupState?>(null)
    val dismissShow = { showGroupFlow.value = null }
    private val showSubsConfigFlow = showGroupFlow.map {
        if (it?.groupKey != null) {
            if (it.appId != null) {
                DbSet.subsConfigDao.queryAppGroupTypeConfig(it.subsId, it.appId, it.groupKey)
            } else {
                DbSet.subsConfigDao.queryGlobalGroupTypeConfig(it.subsId, it.groupKey)
            }
        } else {
            flow { emit(null) }
        }
    }.flatMapLatest { it }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, null)

    val editOrAddGroupFlow = MutableStateFlow<ShowGroupState?>(null)
    val dismissEditOrAdd = { editOrAddGroupFlow.value = null }

    val editExcludeGroupFlow = MutableStateFlow<ShowGroupState?>(null)
    val dismissExcludeGroup = { editExcludeGroupFlow.value = null }

    @Composable
    fun Render() {
        val navController = LocalNavController.current

        val showGroupState = showGroupFlow.collectAsState().value
        val showSubs = useSubs(showGroupState?.subsId)
        val showGroup = useSubsGroup(showSubs, showGroupState?.groupKey, showGroupState?.appId)
        if (showGroupState?.groupKey != null && showSubs != null && showGroup != null) {
            val subsConfig = showSubsConfigFlow.collectAsState().value
            val excludeData = remember(subsConfig?.exclude) {
                ExcludeData.parse(subsConfig?.exclude)
            }
            RuleGroupDialog(
                subs = showSubs,
                group = showGroup,
                appId = showGroupState.appId,
                onDismissRequest = dismissShow,
                onClickEdit = {
                    dismissShow()
                    editOrAddGroupFlow.value = showGroupState
                },
                onClickEditExclude = {
                    dismissShow()
                    if (showGroupState.appId == null) {
                        navController.toDestinationsNavigator().navigate(
                            GlobalGroupExcludePageDestination(
                                showGroupState.subsId,
                                showGroupState.groupKey
                            )
                        )
                    } else {
                        editExcludeGroupFlow.value = showGroupState
                    }
                },
                onClickResetSwitch = subsConfig?.let {
                    if (showGroup is RawSubscription.RawGlobalGroup) {
                        if (showGroupState.pageAppId != null) {
                            if (excludeData.appIds.contains(showGroupState.pageAppId)) {
                                vm.viewModelScope.launchAsFn {
                                    DbSet.subsConfigDao.update(
                                        subsConfig.copy(
                                            exclude = excludeData.clear(
                                                appId = showGroupState.pageAppId
                                            ).stringify()
                                        )
                                    )
                                    toast("已重置局部开关至初始状态")
                                }
                            } else {
                                null
                            }
                        } else {
                            subsConfig.enable?.let {
                                vm.viewModelScope.launchAsFn {
                                    DbSet.subsConfigDao.update(subsConfig.copy(enable = null))
                                    toast("已重置开关至初始状态")
                                }
                            }
                        }
                    } else {
                        subsConfig.enable?.let {
                            vm.viewModelScope.launchAsFn {
                                DbSet.subsConfigDao.update(subsConfig.copy(enable = null))
                                toast("已重置开关至初始状态")
                            }
                        }
                    }
                },
                onClickDelete = vm.viewModelScope.launchAsFn {
                    dismissShow()
                    val r = vm.dialogFlow.getResult(
                        title = "删除规则组",
                        text = "确定删除 ${showGroup.name} ?",
                        error = true,
                    )
                    if (!r) {
                        showGroupFlow.value = showGroupState
                        return@launchAsFn
                    }
                    if (showGroup is RawSubscription.RawGlobalGroup) {
                        updateSubscription(
                            showSubs.copy(
                                globalGroups = showSubs.globalGroups.filter { g -> g.key != showGroup.key }
                            )
                        )
                        DbSet.subsConfigDao.deleteGlobalGroupConfig(
                            showGroupState.subsId,
                            showGroupState.groupKey
                        )
                    } else if (showGroupState.appId != null) {
                        updateSubscription(
                            showSubs.copy(
                                apps = showSubs.apps.map { a ->
                                    if (a.id == showGroupState.appId) {
                                        a.copy(groups = a.groups.filter { g -> g.key != showGroup.key })
                                    } else {
                                        a
                                    }
                                }
                            )
                        )
                        DbSet.subsConfigDao.deleteAppGroupConfig(
                            showGroupState.subsId,
                            showGroupState.appId,
                            showGroupState.groupKey
                        )
                    }
                    toast("删除成功")
                }
            )
        }

        val editOrAddGroupState = editOrAddGroupFlow.collectAsState().value
        val editOrAddSubs = useSubs(editOrAddGroupState?.subsId)
        val editOrAddGroup =
            useSubsGroup(editOrAddSubs, editOrAddGroupState?.groupKey, editOrAddGroupState?.appId)
        if (editOrAddGroupState != null && editOrAddSubs != null) {
            EditOrAddRuleGroupDialog(
                subs = editOrAddSubs,
                group = editOrAddGroup,
                app = if (editOrAddGroupState.addAppRule) {
                    null
                } else {
                    useEditSubsApp(editOrAddSubs, editOrAddGroupState.appId)
                },
                onDismissRequest = dismissEditOrAdd,
                addAnyAppRule = editOrAddGroupState.addAppRule
            )
        }

        val excludeGroupState = editExcludeGroupFlow.collectAsState().value
        val excludeSubs = useSubs(excludeGroupState?.subsId)
        if (excludeGroupState?.groupKey != null && excludeSubs != null) {
            EditGroupExcludeDialog(
                subs = excludeSubs,
                groupKey = excludeGroupState.groupKey,
                appId = excludeGroupState.appId,
                subsConfig = null,
                onDismissRequest = dismissExcludeGroup
            )
        }
    }
}
