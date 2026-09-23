package li.gkd.app.feature.subscription

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.data.ruleconfig.RuleGroupConfigService
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.util.MutexState
import li.gkd.app.text.UiStrings
import li.gkd.app.domain.rule.CategoryPolicy
import li.gkd.app.domain.rule.CategorySetting
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.db.Db
import li.gkd.app.data.RawSubscription
import li.gkd.app.ui.share.BaseViewModel

data class CategorySummary(
    val category: RawSubscription.RawCategory,
    val appCount: Int,
    val groupCount: Int,
    val enabledGroupCount: Int,
    val setting: CategorySetting,
)

data class SubsCategoryUiState(
    val subscription: RawSubscription,
    val categories: List<CategorySummary>,
)

class SubsCategoryVm(route: SubsCategoryRoute) : BaseViewModel() {
    private val mutation = MutexState()
    val busyFlow: StateFlow<Boolean> get() = mutation.state

    suspend fun runAction(action: suspend () -> Unit) {
        mutation.tryWithStateLock(action)
    }

    suspend fun setSettings(state: SubsCategoryUiState, keys: Set<Int>, setting: CategorySetting) {
        val expected = state.categories.filter { it.category.key in keys }
            .associate { it.category.key to it.setting }
        check(expected.size == keys.size) { UiStrings.category_missing }
        RuleGroupConfigService.setCategorySettings(state.subscription, expected, setting)
    }

    suspend fun deleteCategories(state: SubsCategoryUiState, keys: Set<Int>) {
        SubscriptionRepository.deleteCategories(state.subscription, keys)
    }

    val uiState = requiredSubscription(route.subsItemId).buildUiState { subscription ->
        Db.subscriptionConfigStore.observe().map { snapshot ->
            val categoryConfigs = snapshot.categoryConfigs.filter { it.subsId == subscription.id }
                .associateBy { it.categoryKey }
            val groupConfigs = snapshot.appGroupConfigs.filter { it.subsId == subscription.id }
                .associateBy { it.appId to it.groupKey }
            SubsCategoryUiState(subscription, subscription.categories.map { category ->
                val apps = subscription.getCategoryApps(category.key)
                val config = categoryConfigs[category.key]
                CategorySummary(
                    category = category,
                    appCount = apps.size,
                    groupCount = apps.sumOf { it.groups.size },
                    enabledGroupCount = apps.sumOf { app ->
                        app.groups.count { group ->
                            RuleGroupPolicy.getGroupEnabled(group, groupConfigs[app.id to group.key], category, config)
                        }
                    },
                    setting = CategoryPolicy.setting(config),
                )
            })
        }
    }

}
