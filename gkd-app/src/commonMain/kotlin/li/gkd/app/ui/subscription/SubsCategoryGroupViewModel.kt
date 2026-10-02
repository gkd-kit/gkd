package li.gkd.app.ui.subscription

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import li.gkd.app.resources.Res
import li.gkd.app.resources.category_removed_back_notice
import li.gkd.app.resources.category_selected_rules_changed
import li.gkd.app.rule.CategoryPolicy
import li.gkd.app.rule.CategorySetting
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.toSwitchTarget
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.MutexState
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.navigation.SubsCategoryGroupRoute
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.ui.text.getSync
import li.gkd.db.Db
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore

data class SubsCategoryGroupUiState(
    val subscription: RawSubscription,
    val category: RawSubscription.RawCategory,
    val apps: List<RawSubscription.RawApp>,
    val groupConfigs: Map<RuleGroupTarget.App, SubsAppGroupConfig>,
    val categoryConfig: SubsCategoryConfig?,
    val appActionOrder: Map<String, Int>,
    val configs: SubscriptionConfigSnapshot,
) {
    val targets: Set<RuleGroupTarget.App> = apps.flatMap { app ->
        app.groups.map { RuleGroupTarget.App(subscription.id, app.id, it.key) }
    }.toSet()
    val overrideTargets: Set<RuleGroupTarget.App> = targets.filterTo(mutableSetOf()) {
        groupConfigs[it]?.enable != null
    }
    val setting: CategorySetting = CategoryPolicy.setting(categoryConfig)
}

class SubsCategoryGroupViewModel(
    private val route: SubsCategoryGroupRoute,
) : BaseViewModel() {
    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state

    suspend fun runAction(action: suspend () -> Unit) {
        mutation.tryWithStateLock(action)
    }

    val uiState = RequiredSubscription(route.subsId, scope).buildUiState { subscription ->
        combine(
            SubscriptionConfigStore.observe(),
            Db.actionLogDao.queryLatestUniqueAppIds(route.subsId),
        ) { snapshot, actionAppIds ->
            val disabledAppIds = snapshot.appConfigs.filter {
                it.subsId == subscription.id && it.enable == false
            }.mapTo(mutableSetOf()) { it.appId }
            SubsCategoryGroupUiState(
                configs = snapshot,
                subscription = subscription,
                category = subscription.categories.find { it.key == route.categoryKey }
                    ?: error(Res.string.category_removed_back_notice.getSync()),
                apps = subscription.getCategoryApps(route.categoryKey)
                    .filter { it.id !in disabledAppIds },
                groupConfigs = snapshot.appGroupConfigs.filter { it.subsId == subscription.id }
                    .associateBy { RuleGroupTarget.App(it.subsId, it.appId, it.groupKey) },
                categoryConfig = snapshot.categoryConfigs.find {
                    it.subsId == subscription.id && it.categoryKey == route.categoryKey
                },
                appActionOrder = actionAppIds.mapIndexed { index, id -> id to index }.toMap(),
            )
        }
    }

    fun setSortType(option: AppSortOption) {
        SettingsRepository.updateSettings { it.copy(subsCategorySort = option.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(subsCategoryGroupType = value) }
    }

    fun toggleShowBlockApps() {
        SettingsRepository.updateSettings { it.copy(subsCategoryShowBlock = !it.subsCategoryShowBlock) }
    }

    suspend fun setCategorySetting(
        subscription: RawSubscription,
        setting: CategorySetting,
        expected: CategorySetting
    ) {
        RuleGroupConfigService.setCategorySetting(
            subscription,
            route.categoryKey,
            setting,
            expected
        )
    }

    fun prepareSwitches(
        state: SubsCategoryGroupUiState,
        targets: Set<RuleGroupTarget.App>
    ): RuleSwitchRequest {
        check(state.targets.containsAll(targets)) { Res.string.category_selected_rules_changed.getSync() }
        return RuleGroupConfigService.prepare(
            targets.map { it.toSwitchTarget() },
            listOf(state.subscription),
            state.configs
        )
    }

    suspend fun applySwitches(request: RuleSwitchRequest, setting: RuleSetting) =
        RuleGroupConfigService.apply(request, setting)

    suspend fun deleteCategory(subscription: RawSubscription) {
        SubscriptionRepository.deleteCategory(subscription, route.categoryKey)
    }
}
