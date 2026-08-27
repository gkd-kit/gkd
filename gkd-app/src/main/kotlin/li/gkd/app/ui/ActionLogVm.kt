package li.gkd.app.ui

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import li.gkd.db.ActionLog
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsConfig
import li.gkd.db.Db
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.subsMapFlow

data class ActionLogListItem(
    val actionLog: ActionLog,
    val group: RawSubscription.RawGroupProps?,
    val rule: RawSubscription.RawRuleProps?,
    val subscription: RawSubscription?,
)

data class ActionLogDialogState(
    val actionLog: ActionLog,
    val subsConfig: SubsConfig?,
    val globalAppChecked: Boolean?,
    val activityDisabled: Boolean,
)

class ActionLogVm(val route: ActionLogRoute) : BaseViewModel() {

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        when {
            route.subsId != null -> Db.actionLogDao.pagingSubsSource(subsId = route.subsId)
            route.appId != null -> Db.actionLogDao.pagingAppSource(appId = route.appId)
            else -> Db.actionLogDao.pagingSource()
        }
    }
        .flow
        .cachedIn(scope)
        .combine(subsMapFlow) { pagingData, subsMap ->
            pagingData.map { actionLog ->
                val subscription = subsMap[actionLog.subsId]
                val group = if (actionLog.groupType == SubsConfig.AppGroupType) {
                    subscription?.apps
                        ?.find { app -> app.id == actionLog.appId }
                        ?.groups
                        ?.find { group -> group.key == actionLog.groupKey }
                } else {
                    subscription?.globalGroups?.find { group -> group.key == actionLog.groupKey }
                }
                val rule = group?.rules?.run {
                    if (actionLog.ruleKey != null) {
                        find { rule -> rule.key == actionLog.ruleKey }
                    } else {
                        getOrNull(actionLog.ruleIndex)
                    }
                }
                ActionLogListItem(actionLog, group, rule, subscription)
            }
        }

    private val selectedActionLogFlow = MutableStateFlow<ActionLog?>(null)

    val dialogStateFlow = selectedActionLogFlow.flatMapLatest { actionLog ->
        if (actionLog == null) {
            flowOf(null)
        } else {
            val configFlow = if (actionLog.groupType == SubsConfig.AppGroupType) {
                Db.subsConfigDao.queryAppGroupTypeConfig(
                    actionLog.subsId,
                    actionLog.appId,
                    actionLog.groupKey,
                )
            } else {
                Db.subsConfigDao.queryGlobalGroupTypeConfig(
                    actionLog.subsId,
                    actionLog.groupKey,
                )
            }
            combine(configFlow, subsMapFlow) { subsConfig, subsMap ->
                val subscription = subsMap[actionLog.subsId]
                val exclude = ExcludeData.parse(subsConfig?.exclude)
                val globalAppChecked = if (actionLog.groupType == SubsConfig.GlobalGroupType) {
                    subscription?.globalGroups
                        ?.find { group -> group.key == actionLog.groupKey }
                        ?.let { group ->
                            getGlobalGroupChecked(subscription, exclude, group, actionLog.appId)
                        }
                } else {
                    null
                }
                ActionLogDialogState(
                    actionLog = actionLog,
                    subsConfig = subsConfig,
                    globalAppChecked = globalAppChecked,
                    activityDisabled = actionLog.activityId?.let { activityId ->
                        exclude.activityIds.contains(actionLog.appId to activityId)
                    } ?: false,
                )
            }
        }
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        null,
    )

    fun showActionLog(actionLog: ActionLog) {
        selectedActionLogFlow.value = actionLog
    }

    fun dismissActionLog() {
        selectedActionLogFlow.value = null
    }

    suspend fun deleteLogs() {
        when {
            route.subsId != null -> Db.actionLogDao.deleteSubsAll(route.subsId)
            route.appId != null -> Db.actionLogDao.deleteAppAll(route.appId)
            else -> Db.actionLogDao.deleteAll()
        }
    }

    suspend fun toggleGlobalAppExclusion() {
        val state = dialogStateFlow.value ?: return
        val checked = state.globalAppChecked ?: return
        val actionLog = state.actionLog
        val subsConfig = state.subsConfig ?: SubsConfig(
            type = SubsConfig.GlobalGroupType,
            subsId = actionLog.subsId,
            groupKey = actionLog.groupKey,
        )
        val oldExclude = ExcludeData.parse(subsConfig.exclude)
        Db.subsConfigDao.insert(
            subsConfig.copy(
                exclude = oldExclude.copy(
                    appIds = oldExclude.appIds.toMutableMap().apply {
                        set(actionLog.appId, checked)
                    },
                ).stringify(),
            ),
        )
    }

    suspend fun toggleActivityExclusion() {
        val state = dialogStateFlow.value ?: return
        val actionLog = state.actionLog
        val activityId = actionLog.activityId ?: return
        val subsConfig = state.subsConfig ?: if (actionLog.groupType == SubsConfig.AppGroupType) {
            SubsConfig(
                type = SubsConfig.AppGroupType,
                subsId = actionLog.subsId,
                appId = actionLog.appId,
                groupKey = actionLog.groupKey,
            )
        } else {
            SubsConfig(
                type = SubsConfig.GlobalGroupType,
                subsId = actionLog.subsId,
                groupKey = actionLog.groupKey,
            )
        }
        val oldExclude = ExcludeData.parse(subsConfig.exclude)
        Db.subsConfigDao.insert(
            subsConfig.copy(
                exclude = oldExclude.switch(actionLog.appId, activityId).stringify(),
            ),
        )
    }
}
