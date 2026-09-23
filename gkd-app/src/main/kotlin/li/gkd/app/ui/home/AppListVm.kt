package li.gkd.app.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.gkd.app.text.UiStrings
import li.gkd.app.MainViewModel
import li.gkd.app.data.AppInfo
import li.gkd.app.permission.PermissionStates
import li.gkd.app.store.AppStore.blockMatchAppListFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.useAppFilter
import li.gkd.app.util.AppSortOption
import li.gkd.app.domain.rule.RuleSummary
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.util.findOption
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.util.switchItem
import li.gkd.app.util.ToastUtils.toast

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
        SubscriptionState.ruleSummaryFlow,
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
        AppInfoRepository.appListAuthAbnormalFlow,
        AppInfoRepository.updating,
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
                ruleSummary = SubscriptionState.ruleSummaryFlow.value,
                whiteListAppIds = blockMatchAppListFlow.value,
            ),
            environment = AppListEnvironment(
                canQueryPackages = PermissionStates.queryPackages.stateFlow.value,
                queryPackagesAbnormal = AppInfoRepository.appListAuthAbnormalFlow.value,
                refreshing = AppInfoRepository.updating.value,
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
        appFilter.updateSearchStr(value.trim())
    }

    fun closeSearch() {
        appFilter.updateSearchStr("")
        showSearchBarFlow.value = false
    }

    fun toggleSearch() {
        if (showSearchBarFlow.value) {
            if (appFilter.searchStrFlow.value.isEmpty()) {
                showSearchBarFlow.value = false
            } else {
                appFilter.updateSearchStr("")
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
        AppStore.updateSettings { it.copy(appSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(appGroupType = value) }
    }

    fun setShowBlockApp(value: Boolean) {
        AppStore.updateSettings { it.copy(showBlockApp = value) }
    }

    fun toggleWhiteList(appId: String) {
        AppStore.updateBlockMatchAppList { it.switchItem(appId) }
    }

    fun refresh() {
        scope.launchUi(Dispatchers.IO) {
            AppInfoRepository.refresh()
            toast(UiStrings.app_list_update_success)
        }
    }
}
