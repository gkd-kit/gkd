package li.gkd.app.feature.log

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
import li.gkd.app.text.UiStrings
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.ruleconfig.RuleSwitchRequest
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.domain.rule.RuleGroupTarget
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.toRuleGroupTarget
import li.gkd.app.domain.rule.toSwitchTarget
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.db.ActionLog
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot

data class ActionLogListItem(
    val actionLog: ActionLog,
    val group: RawSubscription.RawGroupProps?,
    val rule: RawSubscription.RawRuleProps?,
    val subscription: RawSubscription?,
)

data class ActionLogDialogState(
    val actionLog: ActionLog,
    val subsConfig: SubsGroupConfig?,
    val subscription: RawSubscription?,
    val group: RawSubscription.RawGroupProps?,
    val configs: SubscriptionConfigSnapshot,
    val activityDisabled: Boolean,
)

class ActionLogVm(
    val route: ActionLogRoute,
) : BaseViewModel() {

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        when {
            route.subsId != null -> Db.actionLogDao.pagingSubsSource(route.subsId)
            route.appId != null -> Db.actionLogDao.pagingAppSource(route.appId)
            else -> Db.actionLogDao.pagingSource()
        }
    }
        .flow
        .cachedIn(scope)
        .combine(SubscriptionState.subsMapFlow) { pagingData, subsMap ->
            pagingData.map { actionLog ->
                val subscription = subsMap[actionLog.subsId]
                val group = if (actionLog.groupType == RuleGroupType.App) {
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
            combine(Db.subscriptionConfigStore.observe(), SubscriptionState.subsMapFlow) { configs, subsMap ->
                val subscription = subsMap[actionLog.subsId]
                val group = if (actionLog.groupType == RuleGroupType.App) subscription?.getAppGroups(actionLog.appId)?.find { it.key == actionLog.groupKey }
                    else subscription?.globalGroups?.find { it.key == actionLog.groupKey }
                val subsConfig = if (actionLog.groupType == RuleGroupType.App) configs.appGroupConfigs.find {
                    it.subsId == actionLog.subsId && it.appId == actionLog.appId && it.groupKey == actionLog.groupKey
                } else configs.globalGroupConfigs.find { it.subsId == actionLog.subsId && it.groupKey == actionLog.groupKey }
                val exclude = ExcludeData.parse(subsConfig?.exclude)
                ActionLogDialogState(
                    actionLog = actionLog,
                    subsConfig = subsConfig,
                    subscription = subscription,
                    group = group,
                    configs = configs,
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

    fun prepareSwitch(state: ActionLogDialogState): RuleSwitchRequest {
        val subscription = checkNotNull(state.subscription) { UiStrings.subscription_missing }
        val group = checkNotNull(state.group) { UiStrings.rule_missing }
        return RuleGroupConfigService.prepare(listOf(group.toRuleGroupTarget(subscription.id, state.actionLog.appId).toSwitchTarget()),
            listOf(subscription), state.configs)
    }

    suspend fun applySwitch(request: RuleSwitchRequest, setting: RuleSetting) = RuleGroupConfigService.apply(request, setting)

    suspend fun updateActivityExclusion(state: ActionLogDialogState) {
        val actionLog = state.actionLog
        val activityId = actionLog.activityId ?: return
        val target = if (actionLog.groupType == RuleGroupType.App) {
            RuleGroupTarget.App(actionLog.subsId, actionLog.appId, actionLog.groupKey)
        } else {
            RuleGroupTarget.Global(actionLog.subsId, actionLog.groupKey)
        }
        RuleGroupConfigService.setActivityExclusion(checkNotNull(state.subscription) { UiStrings.subscription_missing },
            target, actionLog.appId, activityId, state.activityDisabled, !state.activityDisabled)
    }
}
