package li.gkd.app.ui.subscription

import li.gkd.app.rule.RuleSort
import li.gkd.app.record.TriggerStats
import li.gkd.db.Db
import kotlinx.coroutines.flow.combine
import li.gkd.app.ui.component.MultiSelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.rule.toRuleGroupTarget
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson.toJson5String
import li.gkd.app.subscription.edit
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore
import li.gkd.db.SubscriptionIds

data class SubsAppGroupListUiState(
    val subscription: RawSubscription,
    val app: RawSubscription.RawApp,
    val configs: SubscriptionConfigSnapshot,
    val triggerStats: TriggerStats,
)

class SubsAppGroupListViewModel(
    val route: SubsAppGroupListRoute,
) : BaseViewModel() {
    val sort: StateFlow<RuleSort>
        field = MutableStateFlow(RuleSort.ByDefault)

    fun setSort(value: RuleSort) { sort.value = value }

    val selection = MultiSelectionState<Int>()

    val categorySelection: StateFlow<AppRuleCategorySelection>
        field = MutableStateFlow(
            if (route.focusGroupKey == null) AppRuleCategorySelection.All else AppRuleCategorySelection.FocusedRule
        )

    fun selectCategory(selection: AppRuleCategorySelection) {
        categorySelection.value = selection
    }

    val showAppSetting: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowAppSetting(value: Boolean) {
        showAppSetting.value = value
    }

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

    private val subscription = RequiredSubscription(route.subsItemId, scope)

    val uiState = subscription.buildUiState { rawSubscription ->
        combine(SubscriptionConfigStore.observe(), Db.ruleTriggerStatsDao.observe(subsId = route.subsItemId, appId = route.appId, groupType = li.gkd.db.RuleGroupType.App)) { configs, records ->
            SubsAppGroupListUiState(
                subscription = rawSubscription,
                app = rawSubscription.getApp(
                    route.appId,
                    AppInfoRepository.snapshot?.apps.orEmpty()[route.appId]?.name
                ),
                configs = configs,
                triggerStats = TriggerStats(records),
            )
        }
    }

    suspend fun buildSelectedGroupsText(selectedKeys: Set<Int>): String =
        withContext(Dispatchers.Default) {
            val app = uiState.value.value?.app
                ?: error(Res.string.subscription_app_not_loaded.getSync())
            val groups = app.groups.filter { it.key in selectedKeys }
            check(groups.isNotEmpty()) { Res.string.selected_rules_no_copyable.getSync() }
            toJson5String(
                app.copy(
                    groups = groups,
                ),
            )
        }

    fun prepareAppSwitch(state: SubsAppGroupListUiState): RuleSwitchRequest =
        RuleGroupConfigService.prepare(
            listOf(RuleSwitchTarget.App(route.subsItemId, route.appId)),
            listOf(state.subscription), state.configs
        )

    fun prepareSwitches(state: SubsAppGroupListUiState, keys: Set<Int>): RuleSwitchRequest {
        val targets = state.app.groups.filter { it.key in keys }
            .map { it.toRuleGroupTarget(route.subsItemId, route.appId).toSwitchTarget() }
        check(targets.size == keys.size) { Res.string.selected_rules_reselect.getSync() }
        return RuleGroupConfigService.prepare(targets, listOf(state.subscription), state.configs)
    }

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>): Int {
        check(SubscriptionIds.isLocal(route.subsItemId)) { Res.string.remote_rule_delete_unsupported.getSync() }
        var deletedSize = 0
        subscription.update { current ->
            current.edit {
                deletedSize = removeAppGroups(
                    appId = route.appId,
                    removeAppIfEmpty = true,
                ) { it.key in selectedKeys }.size
            }
        }
        return deletedSize
    }
}
