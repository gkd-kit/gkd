package li.gkd.app.ui.home

import li.gkd.app.app.AppQuery
import li.gkd.app.app.AppSort
import li.gkd.app.settings.SettingsStore
import li.gkd.app.state.Loadable

data class AppListPageState(
    val search: String = "",
    val searchOpen: Boolean = false,
    val editingFilter: Set<String>? = null,
    val query: String = "",
) {
    fun content(
        source: Loadable<AppListSource>,
        settings: SettingsStore,
        blocked: Set<String>
    ): AppListContentState {
        val input = source.value
        val listState = when (source) {
            Loadable.Loading -> Loadable.Loading
            is Loadable.Failure -> source
            is Loadable.Ready -> when (settings.appSort) {
                AppSort.ByActionTime.value -> source.value.actionOrderState
                AppSort.ByUsedTime.value -> source.value.visitOrderState
                else -> Loadable.Ready(Unit)
            }
        }
        // Keep rows stable while the user changes whitelist membership.
        val result = AppQuery.select(
            if (listState is Loadable.Ready) input?.apps.orEmpty() else emptyList(),
            query,
            settings.appGroupType,
            settings.appSort,
            settings.showBlockApp,
            editingFilter ?: blocked,
            input?.actionOrder.orEmpty(),
            input?.visitOrder.orEmpty()
        )
        return AppListContentState(
            appInfos = result.apps,
            searchText = search,
            showSearchBar = searchOpen,
            editWhiteListMode = editingFilter != null,
            showAllApps = result.showAllApps,
            ruleStats = input?.ruleStats.orEmpty(),
            whiteListAppIds = blocked,
            canQueryPackages = input?.canQueryPackages ?: true,
            queryPackagesAbnormal = input?.queryPackagesAbnormal ?: false,
            refreshing = input?.refreshing ?: false,
            listState = listState,
            ruleStatsFailed = input?.ruleStatsFailed ?: false,
            sort = settings.appSort,
            group = settings.appGroupType,
            showBlocked = settings.showBlockApp,
        )
    }
}
