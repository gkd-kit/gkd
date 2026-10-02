package li.gkd.app.ui.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.Res
import li.gkd.app.resources.selected_app_rules_no_copyable
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson.toJson5String
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.option.RuleSortOption
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.db.ActionLog
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.db.SubsItem
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class AppRuleSubscription(val subsItem: SubsItem, val subscription: RawSubscription)

data class AppConfigUiState(
    val configs: SubscriptionConfigSnapshot,
    val subsPairs: List<Pair<AppRuleSubscription, List<RawSubscription.RawGroupProps>>>,
    val latestLogs: List<ActionLog>,
)

class AppConfigViewModel(
    val route: AppConfigRoute,
) : BaseViewModel() {
    private val batchMutex = MutexState()
    fun removeFromWhitelist() {
        SettingsRepository.updateBlockMatchAppList { it - route.appId }
    }

    fun removeFromPartialDisable() {
        SettingsRepository.updateBlockA11yAppList { it - route.appId }
    }

    val batchBusyFlow: StateFlow<Boolean> get() = batchMutex.state

    suspend fun runBatchAction(action: suspend () -> Unit) {
        batchMutex.tryWithStateLock(action)
    }

    fun setRuleSortType(option: RuleSortOption) {
        SettingsRepository.updateSettings { it.copy(appRuleSort = option.value) }
    }

    fun setShowDisabledRules(show: Boolean) {
        SettingsRepository.updateSettings { it.copy(showDisabledRule = show) }
    }

    val uiState: StateFlow<Loadable<AppConfigUiState>> =
        SubscriptionRepository.snapshotFlow.flatMapLatest { source ->
            when (source) {
                Loadable.Loading -> flowOf(Loadable.Loading)
                is Loadable.Failure -> flowOf(source)
                is Loadable.Ready -> combine<SubscriptionConfigSnapshot, List<ActionLog>, Loadable<AppConfigUiState>>(
                    SubscriptionConfigStore.observe(),
                    Db.actionLogDao.queryLatestByAppId(route.appId),
                ) { configs, logs ->
                    val globalConfigs =
                        configs.globalGroupConfigs.associateBy { it.subsId to it.groupKey }
                    Loadable.Ready(
                        AppConfigUiState(
                            configs = configs,
                            subsPairs = configs.subsItems.filter { it.enable }.sortedBy { it.order }
                                .mapNotNull { item ->
                                    val sub = source.value.subscriptions[item.id]
                                        ?: return@mapNotNull null
                                    val appEnabled = configs.appConfigs.find {
                                        it.subsId == item.id && it.appId == route.appId
                                    }?.enable != false
                                    // Parent switches define this summary's scope, even when disabled rules are shown.
                                    val globalGroups = sub.globalGroups.filter { group ->
                                        globalConfigs[item.id to group.key]?.enable ?: group.enable
                                        ?: true
                                    }
                                    val groups =
                                        globalGroups + if (appEnabled) sub.getAppGroups(route.appId) else emptyList()
                                    (AppRuleSubscription(
                                        item,
                                        sub
                                    ) to groups).takeIf { groups.isNotEmpty() }
                                },
                            latestLogs = logs,
                        )
                    )
                }.catch { emit(Loadable.Failure(it)) }
            }
        }.stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    fun prepareSwitches(state: AppConfigUiState, targets: Set<RuleGroupTarget>): RuleSwitchRequest =
        RuleGroupConfigService.prepare(
            targets.map { it.toSwitchTarget() },
            state.subsPairs.map { it.first.subscription }, state.configs
        )

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)

    suspend fun buildSelectedGroupsText(selectedGroups: Set<RuleGroupTarget>): String =
        withContext(Dispatchers.Default) {
            val selectedKeys = selectedGroups.mapTo(mutableSetOf()) {
                Triple(it.subsId, it.groupType, it.groupKey)
            }
            val subsPairs = uiState.value.value?.subsPairs.orEmpty()
            val groups = subsPairs.flatMap { (entry, groups) ->
                groups.filterIsInstance<RawSubscription.RawAppGroup>().filter { group ->
                    Triple(entry.subsItem.id, RuleGroupType.App, group.key) in selectedKeys
                }
            }
            check(groups.isNotEmpty()) { Res.string.selected_app_rules_no_copyable.getSync() }
            toJson5String(
                RawSubscription.RawApp(
                    id = route.appId,
                    name = AppInfoRepository.snapshot?.apps.orEmpty()[route.appId]?.name,
                    groups = groups,
                )
            )
        }

}
