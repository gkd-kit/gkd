package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.FocusGroup
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.share.BaseViewModel

class SubsAppGroupListVm(val route: SubsAppGroupListRoute) : BaseViewModel() {

    val subsFlow = mapSafeSubs(route.subsItemId)
    val subsAppFlow = subsFlow.mapNew { it.getApp(route.appId) }

    val subsConfigsFlow = DbSet.subsConfigDao.queryAppGroupTypeConfig(route.subsItemId, route.appId)
        .stateInit(emptyList())

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)
        .stateInit(emptyList())


    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())

    val focusGroupFlow = MutableStateFlow<FocusGroup?>(
        route.focusGroupKey?.let {
            FocusGroup(
                route.subsItemId,
                route.appId,
                route.focusGroupKey
            )
        }
    )
}