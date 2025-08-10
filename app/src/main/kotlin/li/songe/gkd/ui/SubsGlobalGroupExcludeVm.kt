package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.SubsGlobalGroupExcludePageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.mapState
import li.songe.gkd.util.orderedAppInfosFlow
import li.songe.gkd.util.subsIdToRawFlow

class SubsGlobalGroupExcludeVm(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = SubsGlobalGroupExcludePageDestination.argsFrom(stateHandle)

    val rawSubsFlow = subsIdToRawFlow.mapState(viewModelScope) { it[args.subsItemId] }

    val groupFlow =
        rawSubsFlow.mapState(viewModelScope) { r -> r?.globalGroups?.find { g -> g.key == args.groupKey } }

    val disabledAppSetFlow = groupFlow.map { g ->
        (g?.apps ?: emptyList()).filter { a -> a.enable == false }.map { a -> a.id }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val subsConfigFlow =
        DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId, args.groupKey)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val excludeDataFlow = subsConfigFlow.mapState(viewModelScope) { s -> ExcludeData.parse(s?.exclude) }

    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)

    private val appIdToOrderFlow =
        DbSet.actionLogDao.queryLatestUniqueAppIds(args.subsItemId, args.groupKey).map { appIds ->
            appIds.mapIndexed { index, appId -> appId to index }.toMap()
        }
    val sortTypeFlow = storeFlow.mapState(viewModelScope) {
        SortTypeOption.allSubObject.findOption(it.subsExcludeSortType)
    }
    val showSystemAppFlow = storeFlow.mapState(viewModelScope) { it.subsExcludeShowSystemApp }
    val showHiddenAppFlow = storeFlow.mapState(viewModelScope) { it.subsExcludeShowHiddenApp }
    val showDisabledAppFlow = storeFlow.mapState(viewModelScope) { it.subsExcludeShowDisabledApp }
    val showAppInfosFlow =
        orderedAppInfosFlow.combine(showHiddenAppFlow) { appInfos, showHiddenApp ->
            if (showHiddenApp) {
                appInfos
            } else {
                appInfos.filter { a -> !a.hidden }
            }
        }.combine(showSystemAppFlow) { apps, showSystemApp ->
            if (showSystemApp) {
                apps
            } else {
                apps.filter { a -> !a.isSystem }
            }
        }.let {
            combine(it, sortTypeFlow, appIdToOrderFlow) { apps, sortType, appIdToOrder ->
                when (sortType) {
                    SortTypeOption.SortByAppMtime -> {
                        apps.sortedBy { a -> -a.mtime }
                    }

                    SortTypeOption.SortByTriggerTime -> {
                        apps.sortedBy { a -> appIdToOrder[a.id] ?: Int.MAX_VALUE }
                    }

                    SortTypeOption.SortByName -> {
                        apps
                    }
                }
            }
        }.combine(debounceSearchStrFlow) { apps, str ->
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
        }.let {
            combine(
                it,
                showDisabledAppFlow,
                disabledAppSetFlow
            ) { apps, showDisabledApp, disabledAppSet ->
                if (showDisabledApp || disabledAppSet.isEmpty()) {
                    apps
                } else {
                    apps.filter { a -> !disabledAppSet.contains(a.id) }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}