package li.songe.gkd.ui.share

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.collator
import li.songe.gkd.util.visibleAppInfosFlow

class AppFilter(
    val searchStrFlow: MutableStateFlow<String>,
    val appListFlow: StateFlow<List<AppInfo>>,
    val showAllAppFlow: StateFlow<Boolean>,
)

fun BaseViewModel.useAppFilter(
    appGroupTypeFlow: StateFlow<Int>,
    sortTypeFlow: StateFlow<AppSortOption>,
    appOrderListFlow: StateFlow<List<String>> = MainViewModel.instance.appOrderListFlow,
    showBlockAppFlow: StateFlow<Boolean>? = null,
    blockAppListFlow: StateFlow<Set<String>> = blockMatchAppListFlow,
): AppFilter {

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
        appGroupTypeFlow,
    ) { list, type ->
        if (type == 0) {
            return@combine emptyList()
        }
        if (AppGroupOption.normalObjects.all { it.include(type) }) {
            return@combine list
        }
        var resultList = list
        if (!AppGroupOption.SystemGroup.include(type)) {
            resultList = resultList.filterNot { it.isSystem }
        }
        if (!AppGroupOption.UserGroup.include(type)) {
            resultList = resultList.filterNot { !it.isSystem }
        }
        resultList
    }

    val showAllAppFlow = combine(
        tempListFlow,
        visibleAppInfosFlow,
    ) { a, b ->
        a.size == b.size
    }.stateInit(true)

    val searchStrFlow = MutableStateFlow("")
    val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateInit(searchStrFlow.value)
    val appActionOrderMapFlow = appOrderListFlow.map {
        it.mapIndexed { i, appId -> appId to i }.toMap()
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
        appListFlow = tempListFlow,
        showAllAppFlow = showAllAppFlow,
    )
}

fun BaseViewModel.useSubsAppFilter(
    subsId: Long,
    appsFlow: StateFlow<List<RawSubscription.RawApp>>,
    sortTypeFlow: StateFlow<AppSortOption>,
    appGroupTypeFlow: StateFlow<Int>,
    showBlockAppFlow: StateFlow<Boolean>,
): StateFlow<List<RawSubscription.RawApp>> {
    var tempListFlow: Flow<List<RawSubscription.RawApp>> = appsFlow
    tempListFlow = combine(
        tempListFlow,
        appInfoMapFlow,
    ) { apps, appMap ->
        apps.sortedWith { a, b ->
            // 默认顺序: 已安装(有名字->无名字)->未安装(有名字(来自订阅)->无名字)
            val x = appMap[a.id]?.name ?: a.name?.let { "\uFFFF" + it }
            ?: ("\uFFFF\uFFFF" + a.id)
            val y = appMap[b.id]?.name ?: b.name?.let { "\uFFFF" + it }
            ?: ("\uFFFF\uFFFF" + b.id)
            collator.compare(x, y)
        }
    }
    val appActionOrderMapFlow = DbSet.actionLogDao
        .queryLatestUniqueAppIds(subsId)
        .map {
            it.mapIndexed { i, appId -> appId to i }.toMap()
        }
    tempListFlow = combine(
        tempListFlow,
        sortTypeFlow,
        appActionOrderMapFlow,
        MainViewModel.instance.appVisitOrderMapFlow,
    ) { apps, sortType, appIdToOrder, appVisitOrderMap ->
        when (sortType) {
            AppSortOption.ByActionTime -> {
                apps.sortedBy { a -> appIdToOrder[a.id] ?: Int.MAX_VALUE }
            }

            AppSortOption.ByAppName -> {
                apps
            }

            AppSortOption.ByUsedTime -> {
                apps.sortedBy { a -> appVisitOrderMap[a.id] ?: Int.MAX_VALUE }
            }
        }
    }
    tempListFlow = combine(
        tempListFlow,
        appGroupTypeFlow,
        appInfoMapFlow,
    ) { apps, appGroupType, appMap ->
        if (appGroupType == 0) {
            emptyList()
        } else if (AppGroupOption.allObjects.all { it.include(appGroupType) }) {
            apps
        } else {
            var tempList = apps
            if (!AppGroupOption.SystemGroup.include(appGroupType)) {
                tempList = tempList.filterNot { appMap[it.id]?.isSystem == true }
            }
            if (!AppGroupOption.UserGroup.include(appGroupType)) {
                tempList = tempList.filterNot { appMap[it.id]?.isSystem == false }
            }
            if (!AppGroupOption.UnInstalledGroup.include(appGroupType)) {
                tempList = tempList.filterNot { appMap[it.id] == null }
            }
            tempList
        }
    }
    tempListFlow = combine(
        tempListFlow,
        showBlockAppFlow,
        blockMatchAppListFlow
    ) { apps, showBlock, blockSet ->
        if (showBlock) {
            apps
        } else {
            apps.filterNot { it.id in blockSet }
        }
    }
    return tempListFlow.stateInit(appsFlow.value)
}
