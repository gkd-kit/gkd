package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import li.gkd.app.MainViewModel
import li.gkd.db.AppConfig
import li.gkd.app.data.AppInfo
import li.gkd.app.data.RawSubscription
import li.gkd.db.Db
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.filterSubsApps
import li.gkd.app.ui.share.subsAppActionOrderMapState
import li.gkd.app.ui.share.useSubsAppFilter
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.findOption
import li.gkd.app.util.getGroupEnable

data class SubsAppListUiState(
    val apps: List<RawSubscription.RawApp>,
    val showAllApps: Boolean,
)

class SubsAppListVm(
    val route: SubsAppListRoute,
    private val mainVm: MainViewModel,
) : BaseViewModel() {

    private val subscription = requiredSubscription(route.subsItemId)

    private val appConfigsFlow = Db.appConfigDao.queryAppTypeConfig(route.subsItemId)

    private val groupSubsConfigsFlow =
        Db.subsConfigDao.querySubsGroupTypeConfig(route.subsItemId)

    private val categoryConfigsFlow = Db.categoryConfigDao.queryConfig(route.subsItemId)

    val searchStrFlow: StateFlow<String>
        field = MutableStateFlow("")
    val showSearchBarFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    private val debounceSearchStr = searchStrFlow.debounce(200)
    private val appActionOrderMapState = subsAppActionOrderMapState(route.subsItemId)

    val appConfigMapState = appConfigsFlow.map { configs ->
        configs.associateBy { it.appId }
    }.stateLoadable()

    val enableSizeMapState = subscription.buildUiState { rawSubscription ->
        combine(
            categoryConfigsFlow,
            groupSubsConfigsFlow,
        ) { categoryConfigs, groupSubsConfigs ->
            val categoryConfigMap = categoryConfigs.associateBy { it.categoryKey }
            val groupSubsConfigMap = groupSubsConfigs
                .groupBy { it.appId }
                .mapValues { entry -> entry.value.associateBy { it.groupKey } }
            rawSubscription.apps.associate { rawApp ->
                val enableSize = rawApp.groups.count { group ->
                    val category = rawSubscription.getCategory(group.name)
                    getGroupEnable(
                        group,
                        groupSubsConfigMap[rawApp.id]?.get(group.key),
                        category,
                        category?.key?.let(categoryConfigMap::get),
                    )
                }
                rawApp.id to enableSize
            }
        }
    }

    val uiState = subscription.buildUiState(
        initialValue = ::buildCurrentUiState,
    ) { rawSubscription ->
        val sortedAppsFlow = useSubsAppFilter(
            mainVm = mainVm,
            appsFlow = flowOf(rawSubscription.apps),
            appGroupType = { it.subsAppGroupType },
            sortType = { AppSortOption.objects.findOption(it.subsAppSort) },
            showBlockApps = { it.subsAppShowBlock },
            appActionOrderMapState = appActionOrderMapState,
        )
        val filteredAppsFlow = combine(
            sortedAppsFlow,
            appInfoMapFlow,
            debounceSearchStr,
        ) { list, appMap, searchStr ->
            buildUiState(rawSubscription, list, appMap, searchStr)
        }
        filteredAppsFlow
    }

    private fun buildCurrentUiState(rawSubscription: RawSubscription): SubsAppListUiState {
        val settings = storeFlow.value
        val apps = filterSubsApps(
            apps = rawSubscription.apps,
            appMap = appInfoMapFlow.value,
            settings = settings,
            appActionOrderMap = appActionOrderMapState.value.value.orEmpty(),
            appVisitOrderMap = mainVm.appVisitOrderMapState.value.value.orEmpty(),
            blockSet = blockMatchAppListFlow.value,
            appGroupType = { it.subsAppGroupType },
            sortType = { AppSortOption.objects.findOption(it.subsAppSort) },
            showBlockApps = { it.subsAppShowBlock },
        )
        return buildUiState(
            rawSubscription = rawSubscription,
            apps = apps,
            appMap = appInfoMapFlow.value,
            searchStr = searchStrFlow.value,
        )
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        apps: List<RawSubscription.RawApp>,
        appMap: Map<String, AppInfo>,
        searchStr: String,
    ): SubsAppListUiState {
        val filteredApps = if (searchStr.isBlank()) {
            apps
        } else {
            val results = mutableListOf<RawSubscription.RawApp>()
            val remainingApps = apps.toMutableList()
            //1. 搜索已安装应用名称
            remainingApps.toList().apply { remainingApps.clear() }.forEach { app ->
                if (appMap[app.id]?.name?.contains(searchStr, true) == true) {
                    results.add(app)
                } else {
                    remainingApps.add(app)
                }
            }
            //2. 搜索未安装应用名称
            remainingApps.toList().apply { remainingApps.clear() }.forEach { app ->
                if (appMap[app.id] == null && app.name?.contains(searchStr, true) == true) {
                    results.add(app)
                } else {
                    remainingApps.add(app)
                }
            }
            //3. 搜索应用 id
            remainingApps.forEach { app ->
                if (app.id.contains(searchStr, true)) {
                    results.add(app)
                }
            }
            results
        }
        return SubsAppListUiState(
            apps = filteredApps,
            showAllApps = rawSubscription.apps.size == apps.size,
        )
    }

    fun setSearchText(value: String) {
        searchStrFlow.value = value
    }

    fun setSearchBarVisible(visible: Boolean) {
        showSearchBarFlow.value = visible
    }

    fun setSortType(value: AppSortOption) {
        storeFlow.update { it.copy(subsAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        storeFlow.update { it.copy(subsAppGroupType = value) }
    }

    fun toggleShowBlockApps() {
        storeFlow.update { it.copy(subsAppShowBlock = !it.subsAppShowBlock) }
    }

    suspend fun setAppEnabled(appId: String, enabled: Boolean) {
        val currentConfig = appConfigsFlow.first().find { it.appId == appId }
        val newConfig = currentConfig?.copy(enable = enabled) ?: AppConfig(
            enable = enabled,
            subsId = route.subsItemId,
            appId = appId,
        )
        Db.appConfigDao.insert(newConfig)
    }
}
