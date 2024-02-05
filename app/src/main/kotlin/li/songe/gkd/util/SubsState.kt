package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import li.songe.gkd.appScope
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.GlobalRule
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet

val subsItemsFlow by lazy {
    DbSet.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

val subsIdToRawFlow by lazy {
    MutableStateFlow<Map<Long, RawSubscription>>(emptyMap())
}

private val updateSubsFileMutex by lazy { Mutex() }
fun updateSubscription(subscription: RawSubscription) {
    appScope.launchTry {
        updateSubsFileMutex.withLock {
            val newMap = subsIdToRawFlow.value.toMutableMap()
            if (subscription.id < 0 && newMap[subscription.id]?.version == subscription.version) {
                newMap[subscription.id] = subscription.copy(version = subscription.version + 1)
            } else {
                newMap[subscription.id] = subscription
            }
            subsIdToRawFlow.value = newMap
            withContext(Dispatchers.IO) {
                subsFolder.resolve("${subscription.id}.json")
                    .writeText(json.encodeToString(subscription))
            }
        }
    }
}

fun deleteSubscription(subsId: Long) {
    val newMap = subsIdToRawFlow.value.toMutableMap()
    newMap.remove(subsId)
    subsIdToRawFlow.value = newMap
}

fun getGroupRawEnable(
    rawGroup: RawSubscription.RawGroupProps,
    subsConfigs: List<SubsConfig>,
    category: RawSubscription.RawCategory?,
    categoryConfigs: List<CategoryConfig>
): Boolean {
    // 优先级: 规则用户配置 > 批量配置 > 批量默认 > 规则默认
    val groupConfig = subsConfigs.find { c -> c.groupKey == rawGroup.key }
    // 1.规则用户配置
    return groupConfig?.enable ?: if (category != null) {// 这个规则被批量配置捕获
        val categoryConfig = categoryConfigs.find { c -> c.categoryKey == category.key }
        val enable = if (categoryConfig != null) {
            // 2.批量配置
            categoryConfig.enable
        } else {
            // 3.批量默认
            category.enable
        }
        enable
    } else {
        null
    } ?: rawGroup.enable ?: true
}

data class RuleSummary(
    val globalRules: List<GlobalRule> = emptyList(),
    val globalGroups: List<RawSubscription.RawGlobalGroup> = emptyList(),
    val appIdToRules: Map<String, List<AppRule>> = emptyMap(),
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>> = emptyMap(),
    val appIdToAllGroups: Map<String, List<Pair<RawSubscription.RawAppGroup, Boolean>>> = emptyMap(),
) {
    private val appSize = appIdToRules.keys.size
    private val appGroupSize = appIdToGroups.values.sumOf { s -> s.size }

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
        "暂无规则"
    }
}

val ruleSummaryFlow by lazy {
    combine(
        subsItemsFlow,
        subsIdToRawFlow,
        appInfoCacheFlow,
        DbSet.subsConfigDao.query(),
        DbSet.categoryConfigDao.query(),
    ) { subsItems, subsIdToRaw, appInfoCache, subsConfigs, categoryConfigs ->
        val globalSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.GlobalGroupType }
        val appSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppType }
        val groupSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppGroupType }
        val appRules = mutableMapOf<String, MutableList<AppRule>>()
        val appGroups = mutableMapOf<String, List<RawSubscription.RawAppGroup>>()
        val appAllGroups = mutableMapOf<String, List<Pair<RawSubscription.RawAppGroup, Boolean>>>()
        val globalRules = mutableListOf<GlobalRule>()
        val globalGroups = mutableListOf<RawSubscription.RawGlobalGroup>()
        subsItems.filter { it.enable }.forEach { subsItem ->
            val rawSubs = subsIdToRaw[subsItem.id] ?: return@forEach

            // global scope
            val subGlobalSubsConfigs = globalSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subGlobalGroupToRules =
                mutableMapOf<RawSubscription.RawGlobalGroup, List<GlobalRule>>()
            rawSubs.globalGroups.filter { g ->
                g.valid && (subGlobalSubsConfigs.find { c -> c.groupKey == g.key }?.enable
                    ?: g.enable ?: true)
            }.forEach { groupRaw ->
                globalGroups.add(groupRaw)
                val subRules = groupRaw.rules.map { ruleRaw ->
                    GlobalRule(
                        rule = ruleRaw,
                        group = groupRaw,
                        rawSubs = rawSubs,
                        subsItem = subsItem,
                        exclude = subGlobalSubsConfigs.find { c -> c.groupKey == groupRaw.key }?.exclude
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
            val subAppSubsConfigs = appSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subGroupSubsConfigs = groupSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subCategoryConfigs = categoryConfigs.filter { c -> c.subsItemId == subsItem.id }
            rawSubs.apps.filter { appRaw ->
                // 筛选 当前启用的 app 订阅规则
                appRaw.groups.isNotEmpty() && (subAppSubsConfigs.find { c -> c.appId == appRaw.id }?.enable
                    ?: (appInfoCache[appRaw.id] != null))
            }.forEach { appRaw ->
                val subAppGroups = mutableListOf<RawSubscription.RawAppGroup>()
                val appGroupConfigs = subGroupSubsConfigs.filter { c -> c.appId == appRaw.id }
                val subAppGroupToRules = mutableMapOf<RawSubscription.RawAppGroup, List<AppRule>>()
                val groupAndEnables = appRaw.groups.map { groupRaw ->
                    val enable = groupRaw.valid && getGroupRawEnable(
                        groupRaw,
                        appGroupConfigs,
                        rawSubs.groupToCategoryMap[groupRaw],
                        subCategoryConfigs
                    )
                    groupRaw to enable
                }
                appAllGroups[appRaw.id] = (appAllGroups[appRaw.id] ?: emptyList()) + groupAndEnables
                groupAndEnables.forEach { (groupRaw, enable) ->
                    if (enable) {
                        subAppGroups.add(groupRaw)
                        val subRules = groupRaw.rules.map { ruleRaw ->
                            AppRule(
                                rule = ruleRaw,
                                group = groupRaw,
                                app = appRaw,
                                rawSubs = rawSubs,
                                subsItem = subsItem,
                                exclude = appGroupConfigs.find { c -> c.groupKey == groupRaw.key }?.exclude
                            )
                        }
                        subAppGroupToRules[groupRaw] = subRules
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
    }.stateIn(appScope, SharingStarted.Eagerly, RuleSummary())
}

fun initSubsState() {
    subsItemsFlow.value
    appScope.launchTry(Dispatchers.IO) {
        if (subsFolder.exists() && subsFolder.isDirectory) {
            val fileRegex = Regex("^-?\\d+\\.json$")
            val files =
                subsFolder.listFiles { f -> f.isFile && f.name.matches(fileRegex) } ?: emptyArray()
            val subscriptions = files.mapNotNull { f ->
                try {
                    RawSubscription.parse(f.readText())
                } catch (e: Exception) {
                    LogUtils.d("加载订阅文件失败", e)
                    null
                }
            }
            val newMap = subsIdToRawFlow.value.toMutableMap()
            subscriptions.forEach { s ->
                newMap[s.id] = s
            }
            if (newMap[-2] == null) {
                newMap[-2] = RawSubscription(
                    id = -2,
                    name = "本地订阅",
                    version = 0,
                    author = "gkd",
                )
            }
            subsIdToRawFlow.value = newMap
        }
    }
}