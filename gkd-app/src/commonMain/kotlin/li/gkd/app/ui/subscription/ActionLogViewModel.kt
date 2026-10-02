package li.gkd.app.ui.subscription

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.Res
import li.gkd.app.resources.rule_missing
import li.gkd.app.resources.subscription_missing
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.db.ActionLog
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class ActionLogListItem(
    val actionLog: ActionLog,
    val group: RawSubscription.RawGroupProps?,
    val rule: RawSubscription.RawRuleProps?,
    val subscription: RawSubscription?,
    val subscriptionResolved: Boolean,
)

data class ActionLogDialogState(
    val actionLog: ActionLog,
    val subsConfig: SubsGroupConfig?,
    val subscription: RawSubscription?,
    val group: RawSubscription.RawGroupProps?,
    val configs: Loadable<SubscriptionConfigSnapshot>,
    val activityDisabled: Boolean,
)

class ActionLogViewModel(
    val route: ActionLogRoute,
) : BaseViewModel() {

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        val subsId = route.subsId
        val appId = route.appId
        when {
            subsId != null -> Db.actionLogDao.pagingSubsSource(subsId)
            appId != null -> Db.actionLogDao.pagingAppSource(appId)
            else -> Db.actionLogDao.pagingSource()
        }
    }
        .flow
        .cachedIn(scope)
        .combine(SubscriptionRepository.snapshotFlow) { pagingData, snapshot ->
            val subsMap = snapshot.value?.subscriptions.orEmpty()
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
                ActionLogListItem(
                    actionLog, group, rule, subscription,
                    subscription != null || (snapshot is Loadable.Ready && actionLog.subsId !in snapshot.value.loadErrors)
                )
            }
        }

    private val selectedActionLogFlow = MutableStateFlow<ActionLog?>(null)

    val dialogStateFlow = selectedActionLogFlow.flatMapLatest { actionLog ->
        if (actionLog == null) {
            flowOf(null)
        } else {
            combine(
                SubscriptionConfigStore.observe()
                    .map<SubscriptionConfigSnapshot, Loadable<SubscriptionConfigSnapshot>> {
                        Loadable.Ready(
                            it
                        )
                    }
                    .onStart { emit(Loadable.Loading) }
                    .catch { emit(Loadable.Failure(it)) },
                SubscriptionRepository.snapshotFlow
            ) { configs, snapshot ->
                val subsMap = snapshot.value?.subscriptions.orEmpty()
                val subscription = subsMap[actionLog.subsId]
                val group =
                    if (actionLog.groupType == RuleGroupType.App) subscription?.getAppGroups(
                        actionLog.appId
                    )?.find { it.key == actionLog.groupKey }
                    else subscription?.globalGroups?.find { it.key == actionLog.groupKey }
                val subsConfig =
                    if (actionLog.groupType == RuleGroupType.App) configs.value?.appGroupConfigs?.find {
                        it.subsId == actionLog.subsId && it.appId == actionLog.appId && it.groupKey == actionLog.groupKey
                    } else configs.value?.globalGroupConfigs?.find { it.subsId == actionLog.subsId && it.groupKey == actionLog.groupKey }
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
        val subscription =
            checkNotNull(state.subscription) { Res.string.subscription_missing.getSync() }
        val group =
            checkNotNull(state.group) { Res.string.rule_missing.getSync() }
        return RuleGroupConfigService.prepare(
            listOf(
                group.toRuleGroupTarget(subscription.id, state.actionLog.appId).toSwitchTarget()
            ),
            listOf(subscription), checkNotNull(state.configs.value)
        )
    }

    suspend fun applySwitch(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)

    suspend fun updateActivityExclusion(state: ActionLogDialogState) {
        val actionLog = state.actionLog
        val activityId = actionLog.activityId ?: return
        val target = if (actionLog.groupType == RuleGroupType.App) {
            RuleGroupTarget.App(actionLog.subsId, actionLog.appId, actionLog.groupKey)
        } else {
            RuleGroupTarget.Global(actionLog.subsId, actionLog.groupKey)
        }
        RuleGroupConfigService.setActivityExclusion(
            checkNotNull(state.subscription) { Res.string.subscription_missing.getSync() },
            target, actionLog.appId, activityId, state.activityDisabled, !state.activityDisabled
        )
    }
}
