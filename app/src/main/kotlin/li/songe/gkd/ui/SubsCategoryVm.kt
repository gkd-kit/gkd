package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.SubsCategoryPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.map
import li.songe.gkd.util.subsIdToRawFlow

class SubsCategoryVm(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = SubsCategoryPageDestination.argsFrom(stateHandle)

    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { m -> m[args.subsItemId] }

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoryConfigMapFlow = categoryConfigsFlow.map { it.associateBy { it.categoryKey } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val editCategoryFlow = MutableStateFlow<RawSubscription.RawCategory?>(null)
    val showAddCategoryFlow = MutableStateFlow(false)
}