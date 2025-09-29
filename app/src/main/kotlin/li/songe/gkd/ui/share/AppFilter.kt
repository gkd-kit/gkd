package li.songe.gkd.ui.share

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.AppInfo
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.visibleAppInfosFlow

fun BaseViewModel.useAppFilter(
    sortTypeFlow: StateFlow<AppSortOption>,
    appOrderListFlow: StateFlow<List<String>> = MainViewModel.instance.appOrderListFlow,
    showBlockAppFlow: StateFlow<Boolean>? = null,
    blockAppListFlow: StateFlow<Set<String>> = blockMatchAppListFlow,
): AppFilter {

    val searchStrFlow = MutableStateFlow("")
    val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateInit(searchStrFlow.value)
    val appActionOrderMapFlow = appOrderListFlow.map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
    }

    var tempListFlow: Flow<List<AppInfo>> = visibleAppInfosFlow

    if (showBlockAppFlow != null) {
        tempListFlow = combine(
            tempListFlow,
            showBlockAppFlow,
            blockAppListFlow,
        ) { appInfos, showBlockApp, blockAppList ->
            if (showBlockApp) {
                appInfos
            } else {
                appInfos.filterNot { it.id in blockAppList }
            }
        }
    }

    tempListFlow = combine(
        tempListFlow,
        sortTypeFlow,
        appActionOrderMapFlow,
        MainViewModel.instance.appVisitOrderMapFlow,
    ) { apps, sortType, appActionOrderMap, appVisitOrderMap ->
        when (sortType) {
            AppSortOption.ByActionTime -> {
                apps.sortedBy { a -> appActionOrderMap[a.id] ?: Int.MAX_VALUE }
            }

            AppSortOption.ByAppName -> {
                apps
            }

            AppSortOption.ByUsedTime -> {
                apps.sortedBy { a -> appVisitOrderMap[a.id] ?: Int.MAX_VALUE }
            }
        }
    }
    tempListFlow = tempListFlow.combine(debounceSearchStrFlow) { apps, str ->
        if (str.isBlank()) {
            apps
        } else {
            (apps.filter { a -> a.name.contains(str, true) } + apps.filter { a ->
                a.id.contains(
                    str,
                    true
                )
            }).distinct()
        }
    }.stateInit(emptyList())
    return AppFilter(
        searchStrFlow = searchStrFlow,
        appListFlow = tempListFlow
    )
}


class AppFilter(
    val searchStrFlow: MutableStateFlow<String>,
    val appListFlow: StateFlow<List<AppInfo>>,
)
