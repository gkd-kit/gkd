package li.songe.gkd.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.EMPTY_RULE_TIP
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.actionCountFlow
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.map
import li.songe.gkd.util.orderedAppInfosFlow
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.usedSubsEntriesFlow

class HomeVm : ViewModel() {

    val latestRecordFlow = DbSet.actionLogDao.queryLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val latestRecordIsGlobalFlow =
        latestRecordFlow.map(viewModelScope) { it?.groupType == SubsConfig.GlobalGroupType }
    val latestRecordDescFlow = combine(
        latestRecordFlow, subsIdToRawFlow, appInfoCacheFlow
    ) { latestRecord, subsIdToRaw, appInfoCache ->
        if (latestRecord == null) return@combine null
        val isAppRule = latestRecord.groupType == SubsConfig.AppGroupType
        val groupName = if (isAppRule) {
            subsIdToRaw[latestRecord.subsId]?.apps?.find { a -> a.id == latestRecord.appId }?.groups?.find { g -> g.key == latestRecord.groupKey }?.name
        } else {
            subsIdToRaw[latestRecord.subsId]?.globalGroups?.find { g -> g.key == latestRecord.groupKey }?.name
        }
        val appName = appInfoCache[latestRecord.appId]?.name
        val appShowName = appName ?: latestRecord.appId
        if (groupName != null) {
            if (groupName.startsWith(appShowName)) {
                groupName
            } else {
                if (isAppRule) {
                    "$appShowName/$groupName"
                } else {
                    "$groupName/$appShowName"
                }
            }
        } else {
            appShowName
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateIn(appScope, SharingStarted.Eagerly, EMPTY_RULE_TIP)
    }

    val usedSubsItemCountFlow = usedSubsEntriesFlow.map(viewModelScope) { it.size }

    private val appIdToOrderFlow = DbSet.actionLogDao.queryLatestUniqueAppIds().map { appIds ->
        appIds.mapIndexed { index, appId -> appId to index }.toMap()
    }

    val sortTypeFlow = storeFlow.map(viewModelScope) { s ->
        SortTypeOption.allSubObject.find { o -> o.value == s.sortType }
            ?: SortTypeOption.SortByName
    }
    val showSystemAppFlow = storeFlow.map(viewModelScope) { s -> s.showSystemApp }
    val showHiddenAppFlow = storeFlow.map(viewModelScope) { s -> s.showHiddenApp }
    val showSearchBarFlow = MutableStateFlow(false)
    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)
    val appInfosFlow =
        combine(orderedAppInfosFlow.combine(showHiddenAppFlow) { appInfos, showHiddenApp ->
            if (showHiddenApp) {
                appInfos
            } else {
                appInfos.filter { a -> !a.hidden }
            }
        }.combine(showSystemAppFlow) { appInfos, showSystemApp ->
            if (showSystemApp) {
                appInfos
            } else {
                appInfos.filter { a -> !a.isSystem }
            }
        }, sortTypeFlow, appIdToOrderFlow) { appInfos, sortType, appIdToOrder ->
            when (sortType) {
                SortTypeOption.SortByAppMtime -> {
                    appInfos.sortedBy { a -> -a.mtime }
                }

                SortTypeOption.SortByTriggerTime -> {
                    appInfos.sortedBy { a -> appIdToOrder[a.id] ?: Int.MAX_VALUE }
                }

                SortTypeOption.SortByName -> {
                    appInfos
                }
            }
        }.combine(debounceSearchStrFlow) { appInfos, str ->
            if (str.isBlank()) {
                appInfos
            } else {
                (appInfos.filter { a -> a.name.contains(str, true) } + appInfos.filter { a ->
                    a.id.contains(
                        str,
                        true
                    )
                }).distinct()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            showSearchBarFlow.collect {
                if (!it) {
                    searchStrFlow.value = ""
                }
            }
        }
    }

}