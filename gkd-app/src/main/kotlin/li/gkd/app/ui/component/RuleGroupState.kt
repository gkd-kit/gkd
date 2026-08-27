package li.gkd.app.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import li.gkd.app.MainViewModel
import li.gkd.db.CategoryConfig
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsConfig
import li.gkd.app.data.edit
import li.gkd.db.Db
import li.gkd.app.ui.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.UpsertRuleGroupRoute
import li.gkd.app.ui.getGlobalGroupChecked
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.SubscriptionStore
import li.gkd.app.util.getGroupEnable
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

data class ShowGroupState(
    val subsId: Long,
    val appId: String? = null,
    val groupKey: Int? = null,
    val pageAppId: String? = null,
) {
    val groupType: Int
        get() = if (appId != null) {
            SubsConfig.AppGroupType
        } else {
            SubsConfig.GlobalGroupType
        }

    suspend fun querySubsConfig(): SubsConfig? = querySubsConfigFlow().first()

    suspend fun queryCategoryConfig(): CategoryConfig? {
        groupKey ?: error("require groupKey")
        val subs = SubscriptionStore.awaitSubscription(subsId)
        val group = if (groupType == SubsConfig.AppGroupType) {
            subs.apps.find { it.id == appId }?.groups
        } else {
            subs.globalGroups
        }?.find { it.key == groupKey } ?: error("require group")
        val category = subs.getCategory(group.name) ?: return null
        return Db.categoryConfigDao.queryCategoryConfig(subsId, category.key).first()
    }
}

private fun ShowGroupState.querySubsConfigFlow(): Flow<SubsConfig?> {
    val groupKey = groupKey ?: error("require groupKey")
    return if (groupType == SubsConfig.AppGroupType) {
        val appId = appId ?: error("require appId")
        Db.subsConfigDao.queryAppGroupTypeConfig(subsId, appId, groupKey)
    } else {
        Db.subsConfigDao.queryGlobalGroupTypeConfig(subsId, groupKey)
    }
}

private data class ExcludeEditSession(
    val groupState: ShowGroupState,
    val originalConfig: SubsConfig?,
)

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

suspend fun updateRuleGroupEnable(
    subscription: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    enabled: Boolean,
) {
    val newConfig = when {
        appId != null && group is RawSubscription.RawGlobalGroup -> {
            val excludeData = ExcludeData.parse(subsConfig?.exclude)
            (subsConfig ?: SubsConfig(
                type = SubsConfig.GlobalGroupType,
                subsId = subscription.id,
                groupKey = group.key,
            )).copy(
                exclude = excludeData.copy(
                    appIds = excludeData.appIds.toMutableMap().apply {
                        set(appId, !enabled)
                    },
                ).stringify(),
            )
        }

        appId != null -> {
            subsConfig?.copy(enable = enabled) ?: SubsConfig(
                type = SubsConfig.AppGroupType,
                subsId = subscription.id,
                appId = appId,
                groupKey = group.key,
                enable = enabled,
            )
        }

        else -> {
            group as RawSubscription.RawGlobalGroup
            subsConfig?.copy(enable = enabled) ?: SubsConfig(
                type = SubsConfig.GlobalGroupType,
                subsId = subscription.id,
                groupKey = group.key,
                enable = enabled,
            )
        }
    }
    Db.subsConfigDao.insert(newConfig)
}

suspend fun batchUpdateGroupEnable(
    groups: Collection<ShowGroupState>,
    enable: Boolean?
): List<Pair<ShowGroupState, SubsConfig>> {
    val diffDataList = groups.map { g ->
        if (g.groupKey == null) return@map null
        val subscription = runCatching {
            SubscriptionStore.awaitSubscription(g.subsId)
        }.getOrNull() ?: return@map null
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
                subscription.getCategory(targetGroup.name),
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
                subscription.getCategory(targetGroup.name),
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
    Db.subsConfigDao.insertAndDelete(
        newSubsConfigs.filterNot { canDeleteList.contains(it) },
        canDeleteList
    )
    return diffDataList
}

class RuleGroupState(
    private val mainVm: MainViewModel,
) {
    private val showGroupFlow = MutableStateFlow<ShowGroupState?>(null)
    private val dismissGroupShow = { showGroupFlow.value = null }

    fun showGroup(state: ShowGroupState) {
        showGroupFlow.value = state
    }

    private val excludeEditSessionFlow = MutableStateFlow<ExcludeEditSession?>(null)
    private val excludeTextFlow = MutableStateFlow("")
    private val dismissExcludeGroupShow = {
        excludeEditSessionFlow.value = null
        excludeTextFlow.value = ""
    }
    private fun getChangedExcludeData(session: ExcludeEditSession): ExcludeData? {
        val oldValue = ExcludeData.parse(session.originalConfig?.exclude)
        val newValue = ExcludeData.parse(
            excludeTextFlow.value,
            requireNotNull(session.groupState.appId),
        )
        return newValue.takeIf { it != oldValue }
    }

    private fun openExcludeEditor(state: ShowGroupState) {
        dismissGroupShow()
        if (state.appId == null) {
            mainVm.navigatePage(
                SubsGlobalGroupExcludeRoute(
                    state.subsId,
                    requireNotNull(state.groupKey),
                ),
            )
            return
        }
        mainVm.scope.launchTry {
            val originalConfig = state.querySubsConfig()
            excludeTextFlow.value = ExcludeData.parse(originalConfig?.exclude)
                .stringify(state.appId)
            excludeEditSessionFlow.value = ExcludeEditSession(state, originalConfig)
        }
    }

    private suspend fun resetGroupSwitch(
        state: ShowGroupState,
        group: RawSubscription.RawGroupProps,
        subsConfig: SubsConfig,
    ): String {
        if (group is RawSubscription.RawGlobalGroup && state.pageAppId != null) {
            val excludeData = ExcludeData.parse(subsConfig.exclude)
            Db.subsConfigDao.update(
                subsConfig.copy(
                    exclude = excludeData.clear(appId = state.pageAppId).stringify(),
                ),
            )
            return "已重置局部开关至默认值"
        }
        Db.subsConfigDao.update(subsConfig.copy(enable = null))
        return "已重置开关至默认值"
    }

    private suspend fun deleteGroup(
        state: ShowGroupState,
    ) {
        val groupKey = requireNotNull(state.groupKey)
        SubscriptionStore.update(state.subsId) { subscription ->
            if (state.appId == null) {
                subscription.edit {
                    if (removeGlobalGroups { it.key == groupKey }.isEmpty()) {
                        error("规则已不存在")
                    }
                }
            } else {
                subscription.edit {
                    if (subscription.apps.none { it.id == state.appId }) {
                        error("应用规则已不存在")
                    }
                    if (removeAppGroups(state.appId) { it.key == groupKey }.isEmpty()) {
                        error("规则已不存在")
                    }
                }
            }
        }
    }

    private suspend fun saveChangedExclude(
        session: ExcludeEditSession,
        subscription: RawSubscription,
        excludeData: ExcludeData,
    ) {
        val state = session.groupState
        val appId = requireNotNull(state.appId)
        val groupKey = requireNotNull(state.groupKey)
        val newSubsConfig = (session.originalConfig ?: SubsConfig(
            type = SubsConfig.AppGroupType,
            subsId = subscription.id,
            appId = appId,
            groupKey = groupKey,
        )).copy(exclude = excludeData.stringify())
        Db.subsConfigDao.insert(newSubsConfig)
    }

    @Composable
    fun Render() {
        val showGroupState = showGroupFlow.collectAsStateWithLifecycle().value
        val showSubs = useSubs(showGroupState?.subsId)
        val showGroup = useSubsGroup(showSubs, showGroupState?.groupKey, showGroupState?.appId)
        if (showGroupState?.groupKey != null && showSubs != null && showGroup != null) {
            val subsConfigFlow = remember(showGroupState) {
                showGroupState.querySubsConfigFlow()
            }
            val subsConfig = subsConfigFlow.collectAsStateWithLifecycle(null).value
            val excludeData = remember(subsConfig?.exclude) {
                ExcludeData.parse(subsConfig?.exclude)
            }
            RuleGroupDialog(
                subs = showSubs,
                group = showGroup,
                appId = showGroupState.appId,
                onDismissRequest = dismissGroupShow,
                onClickEdit = {
                    dismissGroupShow()
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = showGroupState.subsId,
                            groupKey = showGroupState.groupKey,
                            appId = showGroupState.appId,
                        )
                    )
                },
                onClickEditExclude = {
                    openExcludeEditor(showGroupState)
                },
                onClickResetSwitch = subsConfig?.let {
                    if (showGroup is RawSubscription.RawGlobalGroup) {
                        if (showGroupState.pageAppId != null) {
                            if (excludeData.appIds.contains(showGroupState.pageAppId)) {
                                mainVm.scope.launchAsFn {
                                    toast(resetGroupSwitch(showGroupState, showGroup, subsConfig))
                                }
                            } else {
                                null
                            }
                        } else {
                            subsConfig.enable?.let {
                                mainVm.scope.launchAsFn {
                                    toast(resetGroupSwitch(showGroupState, showGroup, subsConfig))
                                }
                            }
                        }
                    } else {
                        subsConfig.enable?.let {
                            mainVm.scope.launchAsFn {
                                toast(resetGroupSwitch(showGroupState, showGroup, subsConfig))
                            }
                        }
                    }
                },
                onClickDelete = mainVm.scope.launchAsFn {
                    val r = mainVm.dialogRequests.confirm(
                        title = "删除规则",
                        text = "确定删除 ${showGroup.name} ?",
                        error = true,
                    )
                    if (!r) {
                        return@launchAsFn
                    }
                    deleteGroup(showGroupState)
                    dismissGroupShow()
                    toast("删除成功")
                }
            )
        }

        val excludeEditSession = excludeEditSessionFlow.collectAsStateWithLifecycle().value
        val excludeGroupState = excludeEditSession?.groupState
        val excludeSubs = useSubs(excludeGroupState?.subsId)
        val excludeGroup =
            useSubsGroup(excludeSubs, excludeGroupState?.groupKey, excludeGroupState?.appId)
        if (excludeEditSession != null && excludeGroupState?.groupKey != null && excludeGroupState.appId != null && excludeSubs != null && excludeGroup is RawSubscription.RawAppGroup) {
            FullscreenDialog(onDismissRequest = dismissExcludeGroupShow) {
                val keyboardController = LocalSoftwareKeyboardController.current
                val onBack = mainVm.scope.launchAsFn {
                    keyboardController?.hide()
                    val newValue = getChangedExcludeData(excludeEditSession)
                    if (newValue != null) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "提示",
                            text = "当前内容未保存，是否放弃编辑？",
                        )) return@launchAsFn
                    }
                    dismissExcludeGroupShow()
                }
                BackHandler(onBack = onBack)
                Scaffold(
                    topBar = {
                        PerfTopAppBar(
                            navigationIcon = {
                                PerfIconButton(
                                    imageVector = PerfIcon.Close,
                                    onClick = onBack
                                )
                            },
                            title = {
                                TowLineText(
                                    title = excludeGroup.name,
                                    subtitle = "编辑禁用",
                                )
                            },
                            actions = {
                                PerfIconButton(imageVector = PerfIcon.Save, onClick = throttle {
                                    val newValue = getChangedExcludeData(excludeEditSession)
                                    if (newValue == null) {
                                        toast("无修改")
                                        dismissExcludeGroupShow()
                                    } else {
                                        dismissExcludeGroupShow()
                                        mainVm.scope.launchTry {
                                            saveChangedExclude(
                                                excludeEditSession,
                                                excludeSubs,
                                                newValue,
                                            )
                                            toast("更新成功")
                                        }
                                    }
                                })
                            }
                        )
                    },
                ) { contentPadding ->
                    MultiTextField(
                        modifier = Modifier.scaffoldPadding(contentPadding),
                        textFlow = excludeTextFlow,
                        placeholderText = "请填入需要禁用的 activityId 列表\n每行一个",
                    )
                }
            }
        }
    }
}
