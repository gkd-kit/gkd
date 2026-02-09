package li.songe.gkd.ui

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.asMutableStateFlow
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.findOption

class SubsGlobalGroupExcludeVm(val route: SubsGlobalGroupExcludeRoute) : BaseViewModel() {

    val subsFlow = mapSafeSubs(route.subsItemId)
    val groupFlow = subsFlow.mapNew { r -> r.globalGroups.find { g -> g.key == route.groupKey } }
    val subsConfigFlow = DbSet.subsConfigDao
        .queryGlobalGroupTypeConfig(route.subsItemId, route.groupKey)
        .stateInit(null)
    val excludeDataFlow = subsConfigFlow.mapNew { s -> ExcludeData.parse(s?.exclude) }

    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.subsExcludeSort) },
        setter = {
            storeFlow.value.copy(subsExcludeSort = it.value)
        }
    )
    val showInnerDisabledAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsExcludeShowInnerDisabledApp },
        setter = {
            storeFlow.value.copy(subsExcludeShowInnerDisabledApp = it)
        }
    )
    val showBlockAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsExcludeShowBlockApp },
        setter = {
            storeFlow.value.copy(subsExcludeShowBlockApp = it)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsExcludeAppGroupType },
        setter = {
            storeFlow.value.copy(subsExcludeAppGroupType = it)
        }
    )
    val appFilter = useAppFilter(
        appGroupTypeFlow = appGroupTypeFlow,
        appOrderListFlow = DbSet.actionLogDao.queryLatestUniqueAppIds(
            route.subsItemId,
            route.groupKey
        ).stateInit(emptyList()),
        sortTypeFlow = sortTypeFlow,
        showBlockAppFlow = showBlockAppFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showAppInfosFlow = combine(
        appFilter.appListFlow,
        showInnerDisabledAppFlow,
        subsFlow,
        groupFlow,
    ) { apps, showDisabledApp, rawSubs, group ->
        if (showDisabledApp || group == null) {
            apps
        } else {
            apps.filter { a -> !rawSubs.getGlobalGroupInnerDisabled(group, a.id) }
        }
    }.stateInit(emptyList()).apply {
        launchOnChange {
            resetKey.intValue++
        }
    }
    val resetKey = mutableIntStateOf(0)
    val excludeTextFlow = MutableStateFlow("")
    val editableFlow = MutableStateFlow(false).apply {
        launchOnChange {
            if (it) {
                excludeTextFlow.value = excludeDataFlow.value.stringify()
            }
        }
    }

    val changedValue: ExcludeData?
        get() {
            val newExclude = ExcludeData.parse(excludeTextFlow.value)
            return if (newExclude != excludeDataFlow.value) {
                newExclude
            } else {
                null
            }
        }

}