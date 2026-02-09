package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.share.BaseViewModel

class SubsCategoryVm(val route: SubsCategoryRoute) : BaseViewModel() {
    val subsRawFlow = mapSafeSubs(route.subsItemId)

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)
        .stateInit(emptyList())

    val categoryConfigMapFlow = categoryConfigsFlow.map { it.associateBy { c -> c.categoryKey } }
        .stateInit(emptyMap())

    val editCategoryFlow = MutableStateFlow<RawSubscription.RawCategory?>(null)
    val showAddCategoryFlow = MutableStateFlow(false)
}