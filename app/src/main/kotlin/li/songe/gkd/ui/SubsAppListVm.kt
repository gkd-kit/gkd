package li.songe.gkd.ui

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.asMutableStateFlow
import li.songe.gkd.ui.share.useSubsAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getGroupEnable

class SubsAppListVm(val route: SubsAppListRoute) : BaseViewModel() {

    val subsFlow = mapSafeSubs(route.subsItemId)

    private val appConfigsFlow = DbSet.appConfigDao.queryAppTypeConfig(route.subsItemId)
        .attachLoad().stateInit(emptyList())

    private val groupSubsConfigsFlow =
        DbSet.subsConfigDao.querySubsGroupTypeConfig(route.subsItemId)
            .attachLoad().stateInit(emptyList())

    private val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)
        .attachLoad().stateInit(emptyList())


    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.subsAppSort) },
        setter = {
            storeFlow.value.copy(subsAppSort = it.value)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsAppGroupType },
        setter = { storeFlow.value.copy(subsAppGroupType = it) },
    )
    val showBlockAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsAppShowBlock },
        setter = { storeFlow.value.copy(subsAppShowBlock = it) },
    )

    private val temp1ListFlow = useSubsAppFilter(
        subsId = route.subsItemId,
        appsFlow = subsFlow.mapNew { it.apps },
        sortTypeFlow = sortTypeFlow,
        appGroupTypeFlow = appGroupTypeFlow,
        showBlockAppFlow = showBlockAppFlow,
    )

    val showAllAppFlow = combine(subsFlow, temp1ListFlow) { subs, list ->
        subs.apps.size == list.size
    }.stateInit(false)

    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStr = searchStrFlow.debounce(200).stateInit(searchStrFlow.value)
    val temp3ListFlow = combine(
        temp1ListFlow,
        appInfoMapFlow,
        debounceSearchStr,
    ) { list, appMap, searchStr ->
        val apps = list.map { it to appMap[it.id] }
        if (searchStr.isBlank()) {
            apps
        } else {
            val results = mutableListOf<Pair<RawSubscription.RawApp, AppInfo?>>()
            val tempList = apps.toMutableList()
            //1. 搜索已安装应用名称
            tempList.toList().apply { tempList.clear() }.forEach { a ->
                if (a.second?.name?.contains(searchStr, true) == true) {
                    results.add(a)
                } else {
                    tempList.add(a)
                }
            }
            //2. 搜索未安装应用名称
            tempList.toList().apply { tempList.clear() }.forEach { a ->
                val name = a.first.name
                if (a.second == null && name?.contains(searchStr, true) == true) {
                    results.add(a)
                } else {
                    tempList.add(a)
                }
            }
            //3. 搜索应用 id
            tempList.toList().apply { tempList.clear() }.forEach { a ->
                if (a.first.id.contains(searchStr, true)) {
                    results.add(a)
                } else {
                    tempList.add(a)
                }
            }
            results
        }
    }.stateInit(emptyList())

    val appItemListFlow = combine(
        subsFlow,
        temp3ListFlow,
        categoryConfigsFlow,
        appConfigsFlow,
        groupSubsConfigsFlow,
    ) { subs, apps, categoryConfigs, appConfigs, groupSubsConfigs ->
        apps.map {
            val appGroupSubsConfigs = groupSubsConfigs.filter { s -> s.appId == it.first.id }
            val enableSize = it.first.groups.count { g ->
                val category = subs.getCategory(g.name)
                getGroupEnable(
                    g,
                    appGroupSubsConfigs.find { c -> c.groupKey == g.key },
                    category,
                    categoryConfigs.find { c -> c.categoryKey == category?.key }
                )
            }
            SubsAppInfoItem(
                rawApp = it.first,
                appInfo = it.second,
                appConfig = appConfigs.find { s -> s.appId == it.first.id },
                enableSize = enableSize,
            )
        }
    }.stateInit(emptyList())

    val resetKey = mutableIntStateOf(0)

    init {
        appItemListFlow.mapNew { it.map { a -> a.id } }.launchOnChange {
            resetKey.intValue++
        }
    }
}

data class SubsAppInfoItem(
    val rawApp: RawSubscription.RawApp,
    val appInfo: AppInfo?,
    val appConfig: AppConfig?,
    val enableSize: Int,
) {
    val id get() = rawApp.id
}
