package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.appIdToRulesFlow
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.clickCountFlow
import li.songe.gkd.util.subsIdToRawFlow
import javax.inject.Inject

@HiltViewModel
class ControlVm @Inject constructor() : ViewModel() {
    private val latestRecordFlow =
        DbSet.clickLogDao.queryLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val latestRecordDescFlow = combine(
        latestRecordFlow, subsIdToRawFlow, appInfoCacheFlow
    ) { latestRecord, subsIdToRaw, appInfoCache ->
        if (latestRecord == null) return@combine null
        val groupName =
            subsIdToRaw[latestRecord.subsId]?.apps?.find { a -> a.id == latestRecord.appId }?.groups?.find { g -> g.key == latestRecord.groupKey }?.name
        val appName = appInfoCache[latestRecord.appId]?.name
        val appShowName = appName ?: latestRecord.appId ?: ""
        if (groupName != null) {
            if (groupName.contains(appShowName)) {
                groupName
            } else {
                "$appShowName-$groupName"
            }
        } else {
            appShowName
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val subsStatusFlow = combine(appIdToRulesFlow, clickCountFlow) { appIdToRules, clickCount ->
        val appSize = appIdToRules.keys.size
        val groupSize =
            appIdToRules.values.sumOf { rules -> rules.map { r -> r.group.key }.toSet().size }
        (if (groupSize > 0) {
            "${appSize}应用/${groupSize}规则组"
        } else {
            "暂无规则"
        }) + if (clickCount > 0) "/${clickCount}点击" else ""
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

}