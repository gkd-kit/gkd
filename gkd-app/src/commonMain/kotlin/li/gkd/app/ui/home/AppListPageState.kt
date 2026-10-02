package li.gkd.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import li.gkd.app.app.AppQuery
import li.gkd.app.app.AppSort
import li.gkd.app.settings.SettingsStore
import li.gkd.app.state.Loadable

class AppListPageState(search: String = "", searchOpen: Boolean = false) {
    var search by mutableStateOf(search)
        private set
    var searchOpen by mutableStateOf(searchOpen)
        private set
    private var editingFilter by mutableStateOf<Set<String>?>(null)
    var query by mutableStateOf(search)

    fun setSearchText(value: String) {
        search = value.trim()
    }

    fun closeSearch() {
        search = ""; searchOpen = false
    }

    fun toggleSearch() {
        if (!searchOpen) searchOpen = true
        else if (search.isEmpty()) searchOpen = false
        else search = ""
    }

    fun toggleEdit(blocked: Set<String>) {
        editingFilter = if (editingFilter == null) blocked else null
    }

    fun closeEdit() {
        editingFilter = null
    }

    fun onLeave() {
        searchOpen = searchOpen && search.isNotEmpty(); editingFilter = null
    }

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

@Composable
fun rememberAppListPageState(): AppListPageState {
    val state = rememberSaveable(
        saver = listSaver(
            save = { listOf(it.search, it.searchOpen) },
            restore = { AppListPageState(it[0] as String, it[1] as Boolean) },
        )
    ) { AppListPageState() }
    LaunchedEffect(state.search) { delay(200); state.query = state.search }
    return state
}
