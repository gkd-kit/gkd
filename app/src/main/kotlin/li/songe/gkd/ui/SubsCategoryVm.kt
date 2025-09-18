package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import com.ramcosta.composedestinations.generated.destinations.SubsCategoryPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.share.BaseViewModel

class SubsCategoryVm(stateHandle: SavedStateHandle) : BaseViewModel() {
    private val args = SubsCategoryPageDestination.argsFrom(stateHandle)

    val subsRawFlow = mapSafeSubs(args.subsItemId)

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateInit(emptyList())

    val categoryConfigMapFlow = categoryConfigsFlow.map { it.associateBy { c -> c.categoryKey } }
        .stateInit(emptyMap())

    val editCategoryFlow = MutableStateFlow<RawSubscription.RawCategory?>(null)
    val showAddCategoryFlow = MutableStateFlow(false)
}