package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.NetworkUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.songe.gkd.appScope
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.GlobalRule
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.ResolvedAppGroup
import li.songe.gkd.data.ResolvedGlobalGroup
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubsVersion
import li.songe.gkd.db.DbSet
import li.songe.json5.decodeFromJson5String
import java.net.URI

val subsItemsFlow by lazy {
    DbSet.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

private fun getCheckUpdateUrl(
    subsItem: SubsItem,
    subscription: RawSubscription?,
): String? {
    val checkUpdateUrl = subscription?.checkUpdateUrl ?: return null
    val updateUrl = subscription.updateUrl ?: subsItem.updateUrl ?: return checkUpdateUrl
    try {
        return URI(updateUrl).resolve(checkUpdateUrl).toString()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

sealed class SubsEntryType {
    abstract val subsItem: SubsItem
    abstract val subscription: RawSubscription?
    val checkUpdateUrl by lazy { getCheckUpdateUrl(subsItem, subscription) }
}

data class SubsEntry(
    override val subsItem: SubsItem,
    override val subscription: RawSubscription?,
) : SubsEntryType()

data class UsedSubsEntry(
    override val subsItem: SubsItem,
    override val subscription: RawSubscription,
) : SubsEntryType()

val subsLoadErrorsFlow = MutableStateFlow<Map<Long, Exception>>(emptyMap())
val subsRefreshErrorsFlow = MutableStateFlow<Map<Long, Exception>>(emptyMap())
val subsIdToRawFlow = MutableStateFlow<Map<Long, RawSubscription>>(emptyMap())

val subsEntriesFlow by lazy {
    combine(
        subsItemsFlow,
        subsIdToRawFlow,
    ) { subsItems, subsIdToRaw ->
        subsItems.map { s ->
            SubsEntry(
                subsItem = s,
                subscription = subsIdToRaw[s.id],
            )
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

val usedSubsEntriesFlow by lazy {
    subsEntriesFlow.map {
        it.filter { s -> s.subsItem.enable && s.subscription != null }
            .map { UsedSubsEntry(it.subsItem, it.subscription!!) }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

fun updateSubscription(subscription: RawSubscription) {
    appScope.launchTry {
        updateSubsMutex.withLock {
            val subsId = subscription.id
            val subsName = subscription.name
            val newMap = subsIdToRawFlow.value.toMutableMap()
            if (subsId < 0 && newMap[subsId]?.version == subscription.version) {
                newMap[subsId] = subscription.run {
                    copy(
                        version = version + 1,
                        apps = apps.filterIfNotAll { it.groups.isNotEmpty() }
                            .distinctByIfAny { it.id },
                    )
                }
            } else {
                newMap[subsId] = subscription
            }
            subsIdToRawFlow.value = newMap
            if (subsLoadErrorsFlow.value.contains(subsId)) {
                subsLoadErrorsFlow.update {
                    it.toMutableMap().apply {
                        remove(subsId)
                    }
                }
            }
            withContext(Dispatchers.IO) {
                DbSet.subsItemDao.updateMtime(subsId, System.currentTimeMillis())
                subsFolder.resolve("${subsId}.json")
                    .writeText(json.encodeToString(newMap[subsId]))
            }
            LogUtils.d("更新订阅文件:id=${subsId},name=${subsName}")
        }
    }
}

fun deleteSubscription(vararg subsIds: Long) {
    appScope.launchTry(Dispatchers.IO) {
        updateSubsMutex.mutex.withLock {
            val deleteSize = DbSet.subsItemDao.deleteById(*subsIds)
            if (deleteSize > 0) {
                DbSet.subsConfigDao.deleteBySubsId(*subsIds)
                DbSet.actionLogDao.deleteBySubsId(*subsIds)
                DbSet.categoryConfigDao.deleteBySubsId(*subsIds)
                val newMap = subsIdToRawFlow.value.toMutableMap()
                subsIds.forEach { id ->
                    newMap.remove(id)
                    subsFolder.resolve("$id.json").apply {
                        if (exists()) {
                            delete()
                        }
                    }
                }
                subsIdToRawFlow.value = newMap
                toast("删除订阅成功")
                LogUtils.d("deleteSubscription", subsIds)
            }
        }
    }
}

fun getCategoryEnable(
    category: RawSubscription.RawCategory?,
    categoryConfig: CategoryConfig?,
): Boolean? = if (categoryConfig != null) {
    // 批量配置
    categoryConfig.enable
} else {
    // 批量默认
    category?.enable
}

fun getGroupEnable(
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    category: RawSubscription.RawCategory? = null,
    categoryConfig: CategoryConfig? = null,
): Boolean = group.valid && when (group) {
    // 优先级: 规则用户配置 > 批量配置 > 批量默认 > 规则默认
    is RawSubscription.RawAppGroup -> {
        subsConfig?.enable ?: getCategoryEnable(category, categoryConfig) ?: group.enable ?: true
    }

    is RawSubscription.RawGlobalGroup -> {
        subsConfig?.enable ?: group.enable ?: true
    }
}

data class RuleSummary(
    val globalRules: List<GlobalRule> = emptyList(),
    val globalGroups: List<ResolvedGlobalGroup> = emptyList(),
    val appIdToRules: Map<String, List<AppRule>> = emptyMap(),
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>> = emptyMap(),
    val appIdToAllGroups: Map<String, List<ResolvedAppGroup>> = emptyMap(),
) {
    val appSize = appIdToRules.keys.size
    val appGroupSize = appIdToGroups.values.sumOf { s -> s.size }

    val numText = if (globalGroups.size + appGroupSize > 0) {
        if (globalGroups.isNotEmpty()) {
            "${globalGroups.size}全局" + if (appGroupSize > 0) {
                "/"
            } else {
                ""
            }
        } else {
            ""
        } + if (appGroupSize > 0) {
            "${appSize}应用/${appGroupSize}规则组"
        } else {
            ""
        }
    } else {
        EMPTY_RULE_TIP
    }

    val slowGlobalGroups =
        globalRules.filter { r -> r.isSlow }.distinctBy { r -> r.group }
            .map { r -> r.group to r }
    val slowAppGroups =
        appIdToRules.values.flatten().filter { r -> r.isSlow }.distinctBy { r -> r.group }
            .map { r -> r.group to r }
    val slowGroupCount = slowGlobalGroups.size + slowAppGroups.size
}

val ruleSummaryFlow by lazy {
    combine(
        usedSubsEntriesFlow,
        appInfoCacheFlow,
        DbSet.appConfigDao.queryUsedList(),
        DbSet.subsConfigDao.queryUsedList(),
        DbSet.categoryConfigDao.queryUsedList(),
    ) { subsEntries, appInfoCache, appConfigs, subsConfigs, categoryConfigs ->
        val globalSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.GlobalGroupType }
        val groupSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppGroupType }
        val appRules = HashMap<String, MutableList<AppRule>>()
        val appGroups = HashMap<String, List<RawSubscription.RawAppGroup>>()
        val appAllGroups =
            HashMap<String, List<ResolvedAppGroup>>()
        val globalRules = mutableListOf<GlobalRule>()
        val globalGroups = mutableListOf<ResolvedGlobalGroup>()
        subsEntries.forEach { (subsItem, rawSubs) ->
            // global scope
            val subGlobalSubsConfigs = globalSubsConfigs.filter { c -> c.subsId == subsItem.id }
            val subGlobalGroupToRules =
                mutableMapOf<RawSubscription.RawGlobalGroup, List<GlobalRule>>()
            rawSubs.globalGroups.filter { g ->
                (subGlobalSubsConfigs.find { c -> c.groupKey == g.key }?.enable
                    ?: g.enable ?: true) && g.valid
            }.forEach { groupRaw ->
                val config = subGlobalSubsConfigs.find { c -> c.groupKey == groupRaw.key }
                val g = ResolvedGlobalGroup(
                    group = groupRaw,
                    subscription = rawSubs,
                    subsItem = subsItem,
                    config = config
                )
                globalGroups.add(g)
                val subRules = groupRaw.rules.map { ruleRaw ->
                    GlobalRule(
                        rule = ruleRaw,
                        g = g,
                        appInfoCache = appInfoCache,
                    )
                }
                subGlobalGroupToRules[groupRaw] = subRules
                globalRules.addAll(subRules)
            }
            subGlobalGroupToRules.values.forEach {
                it.forEach { r ->
                    r.groupToRules = subGlobalGroupToRules
                }
            }
            subGlobalGroupToRules.clear()

            // app scope
            val subAppConfigs = appConfigs.filter { c -> c.subsId == subsItem.id }
            val subGroupSubsConfigs = groupSubsConfigs.filter { c -> c.subsId == subsItem.id }
            val subCategoryConfigs = categoryConfigs.filter { c -> c.subsId == subsItem.id }
            rawSubs.apps.filter { appRaw ->
                // 筛选 当前启用的 app 订阅规则
                appRaw.groups.isNotEmpty() && (subAppConfigs.find { c -> c.appId == appRaw.id }?.enable
                    ?: (appInfoCache[appRaw.id] != null))
            }.forEach { appRaw ->
                val subAppGroups = mutableListOf<RawSubscription.RawAppGroup>()
                val appGroupConfigs = subGroupSubsConfigs.filter { c -> c.appId == appRaw.id }
                val subAppGroupToRules = mutableMapOf<RawSubscription.RawAppGroup, List<AppRule>>()
                val groupAndEnables = appRaw.groups.map { group ->
                    val config = appGroupConfigs.find { c -> c.groupKey == group.key }
                    val category = rawSubs.groupToCategoryMap[group]
                    val categoryConfig =
                        subCategoryConfigs.find { c -> c.categoryKey == category?.key }
                    val enable = getGroupEnable(
                        group,
                        config,
                        category,
                        categoryConfig
                    ) && group.valid
                    ResolvedAppGroup(
                        group = group,
                        subscription = rawSubs,
                        subsItem = subsItem,
                        config = config,
                        app = appRaw,
                        enable = enable,
                    )
                }
                appAllGroups[appRaw.id] = (appAllGroups[appRaw.id] ?: emptyList()) + groupAndEnables
                groupAndEnables.forEach { g ->
                    if (g.enable) {
                        subAppGroups.add(g.group)
                        val subRules = g.group.rules.map { ruleRaw ->
                            AppRule(
                                rule = ruleRaw,
                                g = g,
                                appInfo = appInfoCache[appRaw.id]
                            )
                        }.filter { r -> r.enable }
                        subAppGroupToRules[g.group] = subRules
                        if (subRules.isNotEmpty()) {
                            val rules = appRules[appRaw.id] ?: mutableListOf()
                            appRules[appRaw.id] = rules
                            rules.addAll(subRules)
                        }
                    }
                }
                if (subAppGroups.isNotEmpty()) {
                    appGroups[appRaw.id] = subAppGroups
                }
                subAppGroupToRules.values.forEach {
                    it.forEach { r ->
                        r.groupToRules = subAppGroupToRules
                    }
                }
            }
        }
        RuleSummary(
            globalRules = globalRules,
            globalGroups = globalGroups,
            appIdToRules = appRules,
            appIdToGroups = appGroups,
            appIdToAllGroups = appAllGroups
        )
    }.flowOn(Dispatchers.Default).stateIn(appScope, SharingStarted.Eagerly, RuleSummary())
}

fun getSubsStatus(ruleSummary: RuleSummary, count: Long): String {
    return if (count > 0) {
        "${ruleSummary.numText}/${count}触发"
    } else {
        ruleSummary.numText
    }
}

private fun loadSubs(id: Long): RawSubscription {
    val file = subsFolder.resolve("${id}.json")
    if (!file.exists()) {
        // 某些设备出现这种情况
        if (id == LOCAL_SUBS_ID) {
            return RawSubscription(
                id = LOCAL_SUBS_ID,
                name = "本地订阅",
                version = 0
            )
        }
        if (id == LOCAL_HTTP_SUBS_ID) {
            return RawSubscription(
                id = LOCAL_HTTP_SUBS_ID,
                name = "内存订阅",
                version = 0
            )
        }
        error("订阅文件不存在")
    }
    val subscription = try {
        RawSubscription.parse(file.readText(), json5 = false)
    } catch (e: Exception) {
        throw Exception("订阅文件解析失败", e)
    }
    if (subscription.id != id) {
        error("订阅文件id不一致")
    }
    return subscription
}

private fun refreshRawSubsList(items: List<SubsItem>): Boolean {
    if (items.isEmpty()) return false
    val subscriptions = subsIdToRawFlow.value.toMutableMap()
    val errors = subsLoadErrorsFlow.value.toMutableMap()
    var changed = false
    items.forEach { s ->
        try {
            subscriptions[s.id] = loadSubs(s.id)
            errors.remove(s.id)
            changed = true
        } catch (e: Exception) {
            errors[s.id] = e
        }
    }
    subsIdToRawFlow.value = subscriptions
    subsLoadErrorsFlow.value = errors
    return changed
}

fun initSubsState() {
    subsItemsFlow.value
    appScope.launchTry(Dispatchers.IO) {
        updateSubsMutex.withLock {
            val items = DbSet.subsItemDao.queryAll()
            refreshRawSubsList(items)
        }
    }
}

val updateSubsMutex = MutexState()

private suspend fun updateSubs(subsEntry: SubsEntry): RawSubscription? {
    val subsItem = subsEntry.subsItem
    val subsRaw = subsEntry.subscription
    if (subsItem.updateUrl == null || subsItem.id < 0) return null
    val checkUpdateUrl = subsEntry.checkUpdateUrl
    if (checkUpdateUrl != null && subsRaw != null) {
        try {
            val subsVersion = json.decodeFromJson5String<SubsVersion>(
                client.get(checkUpdateUrl).bodyAsText()
            )
            LogUtils.d(
                "快速检测更新:id=${subsRaw.id},version=${subsRaw.version}",
                subsVersion
            )
            if (subsVersion.id == subsRaw.id && subsVersion.version <= subsRaw.version) {
                return null
            }
        } catch (e: Exception) {
            LogUtils.d("快速检测更新失败", subsItem, e)
        }
    }
    val updateUrl = subsRaw?.updateUrl ?: subsItem.updateUrl
    val text = try {
        client.get(updateUrl).bodyAsText()
    } catch (e: Exception) {
        throw Exception("请求更新链接失败", e)
    }
    val newSubsRaw = try {
        RawSubscription.parse(text)
    } catch (e: Exception) {
        throw Exception("解析文本失败", e)
    }
    if (newSubsRaw.id != subsItem.id) {
        error("新id=${newSubsRaw.id}不匹配旧id=${subsItem.id}")
    }
    if (subsRaw != null && newSubsRaw.version <= subsRaw.version) {
        LogUtils.d(
            "版本号不满足条件:id=${subsItem.id}",
            "${subsRaw.version} -> ${newSubsRaw.version}"
        )
        return null
    }
    return newSubsRaw
}

fun checkSubsUpdate(showToast: Boolean = false) = appScope.launchTry(Dispatchers.IO) {
    if (updateSubsMutex.mutex.isLocked) {
        return@launchTry
    }
    updateSubsMutex.withLock {
        if (subsEntriesFlow.value.any { !it.subsItem.isLocal } && !NetworkUtils.isAvailable()) {
            if (showToast) {
                toast("网络不可用")
            }
            return@withLock
        }
        LogUtils.d("开始检测更新")
        // 文件不存在, 重新加载
        val changed = refreshRawSubsList(subsEntriesFlow.value.filter { it.subscription == null }
            .map { it.subsItem })
        if (changed) {
            delay(500)
        }
        var successNum = 0
        subsEntriesFlow.value.filter { !it.subsItem.isLocal }.forEach { subsEntry ->
            try {
                val newSubsRaw = updateSubs(subsEntry)
                if (newSubsRaw != null) {
                    updateSubscription(newSubsRaw)
                    successNum++
                }
                if (subsRefreshErrorsFlow.value.contains(subsEntry.subsItem.id)) {
                    subsRefreshErrorsFlow.update {
                        it.toMutableMap().apply {
                            remove(subsEntry.subsItem.id)
                        }
                    }
                }
            } catch (e: Exception) {
                subsRefreshErrorsFlow.update {
                    it.toMutableMap().apply {
                        set(subsEntry.subsItem.id, e)
                    }
                }
                LogUtils.d("检测更新失败", e)
            }
        }
        if (showToast) {
            if (successNum > 0) {
                toast("更新 $successNum 条订阅")
            } else {
                toast("暂无更新")
            }
        }
        LogUtils.d("结束检测更新")
        delay(500)
    }
}
