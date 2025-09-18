package li.songe.gkd.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.EMPTY_RULE_TIP
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.subsMapFlow
import li.songe.gkd.util.usedSubsEntriesFlow

class HomeVm : BaseViewModel() {
    val latestRecordFlow = DbSet.actionLogDao.queryLatest().stateInit(null)

    val latestRecordIsGlobalFlow =
        latestRecordFlow.mapNew { it?.groupType == SubsConfig.GlobalGroupType }
    val latestRecordDescFlow = combine(
        latestRecordFlow, subsMapFlow, appInfoMapFlow
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
    }.stateInit(null)

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateInit(EMPTY_RULE_TIP)
    }

    val usedSubsItemCountFlow = usedSubsEntriesFlow.mapNew { it.size }

    val sortTypeFlow = storeFlow.mapNew {
        AppSortOption.objects.findOption(it.appSort)
    }
    val showSystemAppFlow = storeFlow.mapNew { s -> s.showSystemApp }
    val showBlockAppFlow = storeFlow.mapNew { s -> s.showBlockApp }

    val editWhiteListModeFlow = MutableStateFlow(false)
    val blockAppListFlow = MutableStateFlow(blockMatchAppListFlow.value).also { stateFlow ->
        combine(blockMatchAppListFlow, editWhiteListModeFlow) { it }.launchCollect {
            if (!editWhiteListModeFlow.value) {
                stateFlow.value = blockMatchAppListFlow.value
            }
        }
    }

    val appFilter = useAppFilter(
        sortTypeFlow = sortTypeFlow,
        showSystemAppFlow = showSystemAppFlow,
        showBlockAppFlow = showBlockAppFlow,
        blockAppListFlow = blockAppListFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow = MutableStateFlow(false)
    val appInfosFlow = appFilter.appListFlow

    init {
        showSearchBarFlow.launchCollect {
            if (!it) {
                searchStrFlow.value = ""
            }
        }
        appInfosFlow.launchOnChange {
            MainViewModel.instance.appListKeyFlow.value++
        }
    }
}
