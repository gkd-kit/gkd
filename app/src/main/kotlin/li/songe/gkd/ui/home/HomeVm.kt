package li.songe.gkd.ui.home

import android.webkit.URLUtil
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.clickCountFlow
import li.songe.gkd.util.client
import li.songe.gkd.util.getSubsStatus
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.orderedAppInfosFlow
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsRefreshingFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

class HomeVm(stateHandle: SavedStateHandle) : ViewModel() {
    init {
        viewModelScope.launch {
            LogUtils.d(this, stateHandle)
        }
    }

    val tabFlow = MutableStateFlow(controlNav)

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

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, clickCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateIn(appScope, SharingStarted.Eagerly, "")
    }

    fun addSubsFromUrl(url: String) = viewModelScope.launchTry(Dispatchers.IO) {
        if (subsRefreshingFlow.value) return@launchTry
        if (!URLUtil.isNetworkUrl(url)) {
            toast("非法链接")
            return@launchTry
        }
        val subItems = subsItemsFlow.value
        if (subItems.any { it.updateUrl == url }) {
            toast("订阅链接已存在")
            return@launchTry
        }
        subsRefreshingFlow.value = true
        try {
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast("下载订阅文件失败")
                return@launchTry
            }
            val newSubsRaw = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast("解析订阅文件失败")
                return@launchTry
            }
            if (subItems.any { it.id == newSubsRaw.id }) {
                toast("订阅已存在")
                return@launchTry
            }
            if (newSubsRaw.id < 0) {
                toast("订阅id不可为${newSubsRaw.id}\n负数id为内部使用")
                return@launchTry
            }
            val newItem = SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            updateSubscription(newSubsRaw)
            DbSet.subsItemDao.insert(newItem)
            toast("成功添加订阅")
        } finally {
            subsRefreshingFlow.value = false
        }
    }

    private val appIdToOrderFlow = DbSet.clickLogDao.queryLatestUniqueAppIds().map { appIds ->
        appIds.mapIndexed { index, appId -> appId to index }.toMap()
    }

    val sortTypeFlow = storeFlow.map(viewModelScope) { s ->
        SortTypeOption.allSubObject.find { o -> o.value == s.sortType } ?: SortTypeOption.SortByName
    }
    val showSystemAppFlow = storeFlow.map(viewModelScope) { s -> s.showSystemApp }
    val showHiddenAppFlow = storeFlow.map(viewModelScope) { s -> s.showHiddenApp }
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

    val showShareDataIdsFlow = MutableStateFlow<Set<Long>?>(null)
}