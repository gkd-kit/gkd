package li.songe.gkd.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.GlobalGroupExcludePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

data class ShowGroupState(
    val subsId: Long,
    val appId: String? = null,
    val groupKey: Int? = null,
    val subsConfig: SubsConfig? = null,
    val pageAppId: String? = null,
)

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
            val subsConfig = showSubsConfigFlow.collectAsState().value ?: showGroupState.subsConfig
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

        val editGroupState = editOrAddGroupFlow.collectAsState().value
        val editSubs = useSubs(editGroupState?.subsId)
        val editGroup = useSubsGroup(editSubs, editGroupState?.groupKey, editGroupState?.appId)
        if (editGroupState != null && editSubs != null) {
            EditOrAddRuleGroupDialog(
                subs = editSubs,
                group = editGroup,
                app = useEditSubsApp(editSubs, editGroupState.appId),
                onDismissRequest = dismissEditOrAdd
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
