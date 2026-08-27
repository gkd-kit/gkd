package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import li.gkd.app.MainViewModel
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.batchResetAppGroupEnable
import li.gkd.app.data.edit
import li.gkd.db.Db
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.updateRuleGroupEnable
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.filterSubsApps
import li.gkd.app.ui.share.subsAppActionOrderMapState
import li.gkd.app.ui.share.useSubsAppFilter
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.EnableGroupOption
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.findOption
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.db.CategoryConfig
import li.gkd.db.SubsConfig

data class SubsCategoryGroupConfigs(
    val subsConfigs: List<SubsConfig>,
    val categoryConfig: CategoryConfig?,
)

data class SubsCategoryGroupUiState(
    val subscription: RawSubscription,
    val category: RawSubscription.RawCategory,
    val apps: List<RawSubscription.RawApp>,
    val configs: Loadable<SubsCategoryGroupConfigs>,
    val showAllApps: Boolean,
)

class SubsCategoryGroupVm(
    val route: SubsCategoryGroupRoute,
    private val mainVm: MainViewModel,
) : BaseViewModel() {
    val showEditCategoryDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val subscription = requiredSubscription(route.subsId)
    private val subsConfigsFlow = Db.subsConfigDao.querySubsGroupTypeConfig(route.subsId)
    private val categoryConfigFlow =
        Db.categoryConfigDao.queryCategoryConfig(route.subsId, route.categoryKey)
    private val appActionOrderMapState = subsAppActionOrderMapState(route.subsId)

    val uiState = subscription.buildUiState(
        initialValue = ::buildCurrentUiState,
    ) { rawSubscription ->
        val rawApps = rawSubscription.getCategoryApps(route.categoryKey)
        val appsFlow = useSubsAppFilter(
            mainVm = mainVm,
            appsFlow = flowOf(rawApps),
            appGroupType = { it.subsCategoryGroupType },
            sortType = { AppSortOption.objects.findOption(it.subsCategorySort) },
            showBlockApps = { it.subsCategoryShowBlock },
            appActionOrderMapState = appActionOrderMapState,
        )
        combine(
            appsFlow,
            subsConfigsFlow,
            categoryConfigFlow,
        ) { apps, configs, categoryConfig ->
            buildUiState(
                rawSubscription = rawSubscription,
                apps = apps,
                configs = Loadable.Ready(
                    SubsCategoryGroupConfigs(
                        subsConfigs = configs,
                        categoryConfig = categoryConfig,
                    ),
                ),
            )
        }
    }

    private fun buildCurrentUiState(
        rawSubscription: RawSubscription,
    ): SubsCategoryGroupUiState {
        val settings = storeFlow.value
        val apps = filterSubsApps(
            apps = rawSubscription.getCategoryApps(route.categoryKey),
            appMap = appInfoMapFlow.value,
            settings = settings,
            appActionOrderMap = appActionOrderMapState.value.value.orEmpty(),
            appVisitOrderMap = mainVm.appVisitOrderMapState.value.value.orEmpty(),
            blockSet = blockMatchAppListFlow.value,
            appGroupType = { it.subsCategoryGroupType },
            sortType = { AppSortOption.objects.findOption(it.subsCategorySort) },
            showBlockApps = { it.subsCategoryShowBlock },
        )
        return buildUiState(
            rawSubscription = rawSubscription,
            apps = apps,
            configs = Loadable.Loading,
        )
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        apps: List<RawSubscription.RawApp>,
        configs: Loadable<SubsCategoryGroupConfigs>,
    ) = SubsCategoryGroupUiState(
        subscription = rawSubscription,
        category = rawSubscription.getSafeCategory(route.categoryKey),
        apps = apps,
        configs = configs,
        showAllApps = rawSubscription.getCategoryApps(route.categoryKey).size == apps.size,
    )

    fun setEditCategoryDialogVisible(visible: Boolean) {
        showEditCategoryDialogFlow.value = visible
    }

    fun setSortType(option: AppSortOption) {
        storeFlow.update { it.copy(subsCategorySort = option.value) }
    }

    fun setAppGroupType(value: Int) {
        storeFlow.update { it.copy(subsCategoryGroupType = value) }
    }

    fun toggleShowBlockApps() {
        storeFlow.update { it.copy(subsCategoryShowBlock = !it.subsCategoryShowBlock) }
    }

    suspend fun toggleCategoryEnabled(): String {
        val state = uiState.value.value ?: error("订阅尚未加载")
        val rawSubscription = subscription.requireValue()
        val category = state.category
        val configs = state.configs.value ?: error("类别配置尚未加载")
        val categoryConfig = configs.categoryConfig
        val newValue = when (li.gkd.app.util.getCategoryEnable(category, categoryConfig)) {
            false -> null
            null -> true
            true -> false
        }
        val option = EnableGroupOption.objects.findOption(newValue)
        Db.categoryConfigDao.insert(
            (categoryConfig ?: CategoryConfig(
                enable = option.value,
                subsId = rawSubscription.id,
                categoryKey = category.key,
            )).copy(enable = option.value),
        )
        return option.label
    }

    suspend fun resetAllRuleSwitches(): Int {
        val state = uiState.value.value ?: error("订阅尚未加载")
        val rawSubscription = subscription.requireValue()
        return Db.subsConfigDao.batchResetAppGroupEnable(
            rawSubscription.id,
            state.apps.flatMap { app -> app.groups }.map { group ->
                group to rawSubscription.getAppByGroup(group)
            },
        ).size
    }

    suspend fun setGroupEnabled(
        appId: String,
        group: RawSubscription.RawAppGroup,
        subsConfig: SubsConfig?,
        enabled: Boolean,
    ) {
        val rawSubscription = subscription.requireValue()
        updateRuleGroupEnable(rawSubscription, appId, group, subsConfig, enabled)
    }

    suspend fun updateCategory(name: String, description: String): String {
        val changed = subscription.update { current ->
            val category = current.getSafeCategory(route.categoryKey)
            if (current.categories.any { it.key != category.key && it.name == name }) {
                error("不可添加同名类别")
            }
            if (category.name == name && (category.desc ?: "") == description) {
                current
            } else {
                current.edit {
                    val updated = updateCategory(category.key) {
                        copy(name = name, desc = description)
                    }
                    if (!updated) error("类别已不存在")
                }
            }
        }
        return if (changed) "更新成功" else "未修改"
    }

    suspend fun deleteCategory() {
        subscription.update { current ->
            current.edit { removeCategory(route.categoryKey) }
        }
        Db.categoryConfigDao.deleteByCategoryKey(
            route.subsId,
            route.categoryKey,
        )
    }
}
