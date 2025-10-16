package li.songe.gkd.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.SavedStateHandle
import com.ramcosta.composedestinations.generated.destinations.SubsAppListPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.collator
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getGroupEnable

class SubsAppListVm(stateHandle: SavedStateHandle) : BaseViewModel() {
    private val args = SubsAppListPageDestination.argsFrom(stateHandle)

    val subsFlow = mapSafeSubs(args.subsItemId)

    private val appConfigsFlow = DbSet.appConfigDao.queryAppTypeConfig(args.subsItemId)
        .attachLoad().stateInit(emptyList())

    private val groupSubsConfigsFlow = DbSet.subsConfigDao.querySubsGroupTypeConfig(args.subsItemId)
        .attachLoad().stateInit(emptyList())

    private val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .attachLoad().stateInit(emptyList())

    private val temp0ListFlow = combine(subsFlow, appInfoMapFlow) { subs, appInfoCache ->
        subs.usedApps.map {
            it to appInfoCache[it.id]
        }.sortedWith { a, b ->
            // 顺序: 已安装(有名字->无名字)->未安装(有名字(来自订阅)->无名字)
            val x = a.second?.name ?: a.first.name?.let { "\uFFFF" + it }
            ?: ("\uFFFF\uFFFF" + a.first.id)
            val y = b.second?.name ?: b.first.name?.let { "\uFFFF" + it }
            ?: ("\uFFFF\uFFFF" + b.first.id)
            collator.compare(x, y)
        }
    }

    val showUninstallAppFlow = storeFlow.mapNew { it.subsAppShowUninstallApp }
    private val temp1ListFlow = combine(
        temp0ListFlow,
        showUninstallAppFlow,
    ) { apps, showUninstallApp ->
        if (showUninstallApp) {
            apps
        } else {
            apps.filter { it.second != null }
        }
    }

    val sortTypeFlow = storeFlow.mapNew {
        AppSortOption.objects.findOption(it.subsAppSort)
    }
    private val appActionOrderMapFlow = DbSet.actionLogDao
        .queryLatestUniqueAppIds(args.subsItemId)
        .map {
            it.mapIndexed { i, appId -> appId to i }.toMap()
        }
    private val temp2ListFlow = combine(
        temp1ListFlow,
        appActionOrderMapFlow,
        sortTypeFlow,
        MainViewModel.instance.appVisitOrderMapFlow,
    ) { apps, appIdToOrder, sortType, appVisitOrderMap ->
        when (sortType) {

            AppSortOption.ByActionTime -> {
                apps.sortedBy { a -> appIdToOrder[a.first.id] ?: Int.MAX_VALUE }
            }

            AppSortOption.ByAppName -> {
                apps
            }

            AppSortOption.ByUsedTime -> {
                apps.sortedBy { a -> appVisitOrderMap[a.first.id] ?: Int.MAX_VALUE }
            }
        }
    }

    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStr = searchStrFlow.debounce(200).stateInit(searchStrFlow.value)
    val temp3ListFlow = combine(
        temp2ListFlow,
        debounceSearchStr,
    ) { apps, searchStr ->
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
    ) { subsRaw, apps, categoryConfigs, appConfigs, groupSubsConfigs ->
        val groupToCategoryMap = subsRaw.groupToCategoryMap
        apps.map {
            val appGroupSubsConfigs = groupSubsConfigs.filter { s -> s.appId == it.first.id }
            val enableSize = it.first.groups.count { g ->
                getGroupEnable(
                    g,
                    appGroupSubsConfigs.find { c -> c.groupKey == g.key },
                    groupToCategoryMap[g],
                    categoryConfigs.find { c -> c.categoryKey == groupToCategoryMap[g]?.key }
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
