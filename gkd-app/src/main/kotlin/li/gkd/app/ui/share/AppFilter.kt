package li.gkd.app.ui.share

import li.gkd.app.core.state.Loadable

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.MainViewModel
import li.gkd.app.data.AppInfo
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.settings.SettingsStore
import li.gkd.app.store.AppStore.blockMatchAppListFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.util.collator
import li.gkd.db.ActionLog
import li.gkd.db.Db

class AppFilter(
    mutableSearchStrFlow: MutableStateFlow<String>,
    val appListFlow: StateFlow<List<AppInfo>>,
    val showAllAppFlow: StateFlow<Boolean>,
) {
    val searchStrFlow: StateFlow<String>
        field = mutableSearchStrFlow

    fun updateSearchStr(value: String) {
        searchStrFlow.value = value
    }
}

fun BaseViewModel.subsAppActionOrderMapState(
    subsId: Long,
    actionLogDao: ActionLog.ActionLogDao = Db.actionLogDao,
): StateFlow<Loadable<Map<String, Int>>> =
    actionLogDao.queryLatestUniqueAppIds(subsId).map { appIds ->
        appIds.mapIndexed { index, appId -> appId to index }.toMap()
    }.stateLoadable()

fun BaseViewModel.globalGroupAppOrderListState(
    subsId: Long,
    groupKey: Int,
    actionLogDao: ActionLog.ActionLogDao = Db.actionLogDao,
): StateFlow<Loadable<List<String>>> =
    actionLogDao.queryLatestUniqueAppIds(subsId, groupKey).stateLoadable()

fun BaseViewModel.useAppFilter(
    mainVm: MainViewModel,
    appGroupType: (SettingsStore) -> Int,
    sortType: (SettingsStore) -> AppSortOption,
    showBlockApps: ((SettingsStore) -> Boolean)? = null,
    appOrderListState: StateFlow<Loadable<List<String>>> = mainVm.appOrderListState,
    blockAppListFlow: StateFlow<Set<String>> = blockMatchAppListFlow,
): AppFilter {
    val searchStrFlow = MutableStateFlow("")
    val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateInit(searchStrFlow.value)
    val resultFlow = combine(
        AppInfoRepository.visibleAppInfosFlow,
        storeFlow,
        appOrderListState,
        mainVm.appVisitOrderMapState,
        blockAppListFlow,
    ) { visibleApps, settings, appOrderList, appVisitOrderMap, blockAppList ->
        AppFilterInputs(
            visibleApps = visibleApps,
            settings = settings,
            appOrderList = appOrderList.value.orEmpty(),
            appVisitOrderMap = appVisitOrderMap.value.orEmpty(),
            blockAppList = blockAppList,
        )
    }.combine(debounceSearchStrFlow) { inputs, searchStr ->
        buildAppFilterResult(
            inputs = inputs,
            searchStr = searchStr,
            appGroupType = appGroupType,
            sortType = sortType,
            showBlockApps = showBlockApps,
        )
    }.stateInit(
        buildAppFilterResult(
            inputs = AppFilterInputs(
                visibleApps = AppInfoRepository.visibleAppInfosFlow.value,
                settings = storeFlow.value,
                appOrderList = appOrderListState.value.value.orEmpty(),
                appVisitOrderMap = mainVm.appVisitOrderMapState.value.value.orEmpty(),
                blockAppList = blockAppListFlow.value,
            ),
            searchStr = searchStrFlow.value,
            appGroupType = appGroupType,
            sortType = sortType,
            showBlockApps = showBlockApps,
        )
    )
    return AppFilter(
        mutableSearchStrFlow = searchStrFlow,
        appListFlow = resultFlow.mapNew { it.apps },
        showAllAppFlow = resultFlow.mapNew { it.showAllApps },
    )
}

private data class AppFilterInputs(
    val visibleApps: List<AppInfo>,
    val settings: SettingsStore,
    val appOrderList: List<String>,
    val appVisitOrderMap: Map<String, Int>,
    val blockAppList: Set<String>,
)

private data class AppFilterResult(
    val apps: List<AppInfo>,
    val showAllApps: Boolean,
)

private fun buildAppFilterResult(
    inputs: AppFilterInputs,
    searchStr: String,
    appGroupType: (SettingsStore) -> Int,
    sortType: (SettingsStore) -> AppSortOption,
    showBlockApps: ((SettingsStore) -> Boolean)?,
): AppFilterResult {
    var apps = if (showBlockApps == null || showBlockApps(inputs.settings)) {
        inputs.visibleApps
    } else {
        inputs.visibleApps.filterNot { it.id in inputs.blockAppList }
    }
    val type = appGroupType(inputs.settings)
    apps = when {
        type == 0 -> emptyList()
        AppGroupOption.normalObjects.all { it.include(type) } -> apps
        else -> apps.filter { app ->
            if (app.isSystem) {
                AppGroupOption.SystemGroup.include(type)
            } else {
                AppGroupOption.UserGroup.include(type)
            }
        }
    }
    val showAllApps = apps.size == inputs.visibleApps.size
    val actionOrderMap = inputs.appOrderList.mapIndexed { index, appId ->
        appId to index
    }.toMap()
    apps = when (sortType(inputs.settings)) {
        AppSortOption.ByActionTime -> apps.sortedBy { actionOrderMap[it.id] ?: Int.MAX_VALUE }
        AppSortOption.ByAppName -> apps
        AppSortOption.ByUsedTime -> apps.sortedBy {
            inputs.appVisitOrderMap[it.id] ?: Int.MAX_VALUE
        }
    }
    if (searchStr.isNotBlank()) {
        apps = (apps.filter { it.name.contains(searchStr, true) } + apps.filter {
            it.id.contains(searchStr, true)
        }).distinct()
    }
    return AppFilterResult(
        apps = apps,
        showAllApps = showAllApps,
    )
}

fun BaseViewModel.useSubsAppFilter(
    mainVm: MainViewModel,
    appsFlow: Flow<List<RawSubscription.RawApp>>,
    appGroupType: (SettingsStore) -> Int,
    sortType: (SettingsStore) -> AppSortOption,
    showBlockApps: (SettingsStore) -> Boolean,
    appActionOrderMapState: StateFlow<Loadable<Map<String, Int>>>,
): Flow<List<RawSubscription.RawApp>> {
    val filterInputsFlow = combine(
        AppInfoRepository.appInfoMapFlow,
        storeFlow,
        appActionOrderMapState,
        mainVm.appVisitOrderMapState,
        blockMatchAppListFlow,
    ) { appMap, settings, appActionOrderMap, appVisitOrderMap, blockSet ->
        SubsAppFilterInputs(
            appMap = appMap,
            settings = settings,
            appActionOrderMap = appActionOrderMap.value.orEmpty(),
            appVisitOrderMap = appVisitOrderMap.value.orEmpty(),
            blockSet = blockSet,
        )
    }
    return combine(appsFlow, filterInputsFlow) { apps, inputs ->
        filterSubsApps(
            apps = apps,
            appMap = inputs.appMap,
            settings = inputs.settings,
            appActionOrderMap = inputs.appActionOrderMap,
            appVisitOrderMap = inputs.appVisitOrderMap,
            blockSet = inputs.blockSet,
            appGroupType = appGroupType,
            sortType = sortType,
            showBlockApps = showBlockApps,
        )
    }
}

private data class SubsAppFilterInputs(
    val appMap: Map<String, AppInfo>,
    val settings: SettingsStore,
    val appActionOrderMap: Map<String, Int>,
    val appVisitOrderMap: Map<String, Int>,
    val blockSet: Set<String>,
)

fun filterSubsApps(
    apps: List<RawSubscription.RawApp>,
    appMap: Map<String, AppInfo>,
    settings: SettingsStore,
    appActionOrderMap: Map<String, Int>,
    appVisitOrderMap: Map<String, Int>,
    blockSet: Set<String>,
    appGroupType: (SettingsStore) -> Int,
    sortType: (SettingsStore) -> AppSortOption,
    showBlockApps: (SettingsStore) -> Boolean,
): List<RawSubscription.RawApp> {
    var result = apps.sortedWith { a, b ->
        // Default order: Installed (with name->without name) -> Uninstalled (with name(from subscription)->without name)
        val x = appMap[a.id]?.name ?: a.name?.let { "\uFFFF" + it }
        ?: ("\uFFFF\uFFFF" + a.id)
        val y = appMap[b.id]?.name ?: b.name?.let { "\uFFFF" + it }
        ?: ("\uFFFF\uFFFF" + b.id)
        collator.compare(x, y)
    }
    result = when (sortType(settings)) {
        AppSortOption.ByActionTime -> {
            result.sortedBy { appActionOrderMap[it.id] ?: Int.MAX_VALUE }
        }

        AppSortOption.ByAppName -> result

        AppSortOption.ByUsedTime -> {
            result.sortedBy { appVisitOrderMap[it.id] ?: Int.MAX_VALUE }
        }
    }
    val groupType = appGroupType(settings)
    result = when {
        groupType == 0 -> emptyList()
        AppGroupOption.allObjects.all { it.include(groupType) } -> result
        else -> result.filter { app ->
            val appInfo = appMap[app.id]
            when {
                appInfo == null -> AppGroupOption.UnInstalledGroup.include(groupType)
                appInfo.isSystem -> AppGroupOption.SystemGroup.include(groupType)
                else -> AppGroupOption.UserGroup.include(groupType)
            }
        }
    }
    if (!showBlockApps(settings)) {
        result = result.filterNot { it.id in blockSet }
    }
    return result
}
