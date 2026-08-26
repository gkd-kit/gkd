package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.AppInfo
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.RuleSummary
import li.songe.gkd.util.appListAuthAbnormalFlow
import li.songe.gkd.util.findOption
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.updateAllAppInfo
import li.songe.gkd.util.updateAppMutex

data class AppListUiState(
    val appInfos: List<AppInfo>,
    val searchText: String,
    val showSearchBar: Boolean,
    val editWhiteListMode: Boolean,
    val showAllApps: Boolean,
    val ruleSummary: RuleSummary,
    val whiteListAppIds: Set<String>,
    val canQueryPackages: Boolean,
    val queryPackagesAbnormal: Boolean,
    val refreshing: Boolean,
)

private data class AppListControls(
    val showSearchBar: Boolean,
    val editWhiteListMode: Boolean,
)

private data class AppListContentState(
    val appInfos: List<AppInfo>,
    val searchText: String,
    val showAllApps: Boolean,
    val ruleSummary: RuleSummary,
    val whiteListAppIds: Set<String>,
)

private data class AppListEnvironment(
    val canQueryPackages: Boolean,
    val queryPackagesAbnormal: Boolean,
    val refreshing: Boolean,
)

private fun buildAppListUiState(
    controls: AppListControls,
    content: AppListContentState,
    environment: AppListEnvironment,
) = AppListUiState(
    appInfos = content.appInfos,
    searchText = content.searchText,
    showSearchBar = controls.showSearchBar,
    editWhiteListMode = controls.editWhiteListMode,
    showAllApps = content.showAllApps,
    ruleSummary = content.ruleSummary,
    whiteListAppIds = content.whiteListAppIds,
    canQueryPackages = environment.canQueryPackages,
    queryPackagesAbnormal = environment.queryPackagesAbnormal,
    refreshing = environment.refreshing,
)

class AppListVm(mainVm: MainViewModel) : BaseViewModel() {
    private val editWhiteListModeFlow = MutableStateFlow(false)
    private val filterBlockAppListFlow = MutableStateFlow(blockMatchAppListFlow.value)
    private val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.appGroupType },
        sortType = { AppSortOption.objects.findOption(it.appSort) },
        showBlockApps = { it.showBlockApp },
        blockAppListFlow = filterBlockAppListFlow,
    )
    private val showSearchBarFlow = MutableStateFlow(false)

    val appInfosFlow = appFilter.appListFlow

    private val controls = combine(
        showSearchBarFlow,
        editWhiteListModeFlow,
    ) { showSearchBar, editWhiteListMode ->
        AppListControls(
            showSearchBar = showSearchBar,
            editWhiteListMode = editWhiteListMode,
        )
    }
    private val contentState = combine(
        appFilter.appListFlow,
        appFilter.searchStrFlow,
        appFilter.showAllAppFlow,
        ruleSummaryFlow,
        blockMatchAppListFlow,
    ) { appInfos, searchText, showAllApps, ruleSummary, whiteListAppIds ->
        AppListContentState(
            appInfos = appInfos,
            searchText = searchText,
            showAllApps = showAllApps,
            ruleSummary = ruleSummary,
            whiteListAppIds = whiteListAppIds,
        )
    }
    private val environment = combine(
        PermissionStates.queryPackages.stateFlow,
        appListAuthAbnormalFlow,
        updateAppMutex.state,
    ) { canQueryPackages, queryPackagesAbnormal, refreshing ->
        AppListEnvironment(
            canQueryPackages = canQueryPackages,
            queryPackagesAbnormal = queryPackagesAbnormal,
            refreshing = refreshing,
        )
    }

    val uiState = combine(controls, contentState, environment) { controls, content, environment ->
        buildAppListUiState(controls, content, environment)
    }.stateInit(
        buildAppListUiState(
            controls = AppListControls(
                showSearchBar = showSearchBarFlow.value,
                editWhiteListMode = editWhiteListModeFlow.value,
            ),
            content = AppListContentState(
                appInfos = appFilter.appListFlow.value,
                searchText = appFilter.searchStrFlow.value,
                showAllApps = appFilter.showAllAppFlow.value,
                ruleSummary = ruleSummaryFlow.value,
                whiteListAppIds = blockMatchAppListFlow.value,
            ),
            environment = AppListEnvironment(
                canQueryPackages = PermissionStates.queryPackages.stateFlow.value,
                queryPackagesAbnormal = appListAuthAbnormalFlow.value,
                refreshing = updateAppMutex.state.value,
            ),
        ),
    )

    init {
        scope.launch {
            combine(blockMatchAppListFlow, editWhiteListModeFlow) { blockList, editing ->
                blockList to editing
            }.collect { (blockList, editing) ->
                if (!editing) {
                    filterBlockAppListFlow.value = blockList
                }
            }
        }
    }

    fun setSearchText(value: String) {
        appFilter.searchStrFlow.value = value.trim()
    }

    fun closeSearch() {
        appFilter.searchStrFlow.value = ""
        showSearchBarFlow.value = false
    }

    fun toggleSearch() {
        if (showSearchBarFlow.value) {
            if (appFilter.searchStrFlow.value.isEmpty()) {
                showSearchBarFlow.value = false
            } else {
                appFilter.searchStrFlow.value = ""
            }
        } else {
            showSearchBarFlow.value = true
        }
    }

    fun toggleEditWhiteListMode() {
        editWhiteListModeFlow.update { !it }
    }

    fun closeEditWhiteListMode() {
        editWhiteListModeFlow.value = false
    }

    fun onLeaveScreen() {
        if (appFilter.searchStrFlow.value.isEmpty()) {
            showSearchBarFlow.value = false
        }
        editWhiteListModeFlow.value = false
    }

    fun setSortType(value: AppSortOption) {
        storeFlow.update { it.copy(appSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        storeFlow.update { it.copy(appGroupType = value) }
    }

    fun setShowBlockApp(value: Boolean) {
        storeFlow.update { it.copy(showBlockApp = value) }
    }

    fun toggleWhiteList(appId: String) {
        blockMatchAppListFlow.update { it.switchItem(appId) }
    }

    fun refresh() {
        updateAllAppInfo()
    }
}
