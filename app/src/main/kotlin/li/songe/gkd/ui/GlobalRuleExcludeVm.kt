package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.GlobalRuleExcludePageDestination
import li.songe.gkd.util.map
import li.songe.gkd.util.orderedAppInfosFlow
import li.songe.gkd.util.subsIdToRawFlow
import javax.inject.Inject

@HiltViewModel
class GlobalRuleExcludeVm @Inject constructor(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = GlobalRuleExcludePageDestination.argsFrom(stateHandle)

    val rawSubsFlow = subsIdToRawFlow.map(viewModelScope) { it[args.subsItemId] }

    val groupFlow =
        rawSubsFlow.map(viewModelScope) { r -> r?.globalGroups?.find { g -> g.key == args.groupKey } }

    val subsConfigFlow =
        DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId, args.groupKey)
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val excludeDataFlow = subsConfigFlow.map(viewModelScope) { s -> ExcludeData.parse(s?.exclude) }

    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)

    val sortByMtimeFlow = MutableStateFlow(false)
    val showSystemAppFlow = MutableStateFlow(false)
    val showAppInfosFlow = combine(
        debounceSearchStrFlow,
        orderedAppInfosFlow,
        sortByMtimeFlow,
        showSystemAppFlow
    ) { str, list, sortByMtime, showSystemApp ->
        list.let {
            if (sortByMtime) {
                it.sortedBy { a -> -a.mtime }
            } else {
                it
            }
        }.let {
            if (!showSystemApp) {
                it.filter { a -> !a.isSystem }
            } else {
                it
            }
        }.let {
            if (str.isBlank()) {
                it
            } else {
                (it.filter { a -> a.name.contains(str) } + it.filter { a -> a.id.contains(str) }).distinct()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}