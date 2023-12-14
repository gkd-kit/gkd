package li.songe.gkd.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import li.songe.gkd.appScope
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.Rule
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.selector.Selector

val subsItemsFlow by lazy {
    DbSet.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

private val subsIdToMtimeFlow by lazy {
    subsItemsFlow.map { it.sortedBy { s -> s.id }.associate { s -> s.id to s.mtime } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val subsIdToRawFlow by lazy {
    MutableStateFlow<Map<Long, SubscriptionRaw?>>(emptyMap())
}

fun getGroupRawEnable(
    groupRaw: SubscriptionRaw.GroupRaw,
    subsConfigs: List<SubsConfig>,
    category: SubscriptionRaw.Category?,
    categoryConfigs: List<CategoryConfig>
): Boolean {
    // 优先级: 规则用户配置 > 批量配置 > 批量默认 > 规则默认
    val groupConfig = subsConfigs.find { c -> c.groupKey == groupRaw.key }
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
    } ?: groupRaw.enable ?: true
}

private val appIdToRulesFlow by lazy {
    combine(
        subsItemsFlow,
        subsIdToRawFlow,
        DbSet.subsConfigDao.query(),
        DbSet.categoryConfigDao.query(),
    ) { subsItems, subsIdToRaw, subsConfigs, categoryConfigs ->
        val appSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.AppType }
        val groupSubsConfigs = subsConfigs.filter { c -> c.type == SubsConfig.GroupType }
        val appIdToRules = mutableMapOf<String, MutableList<Rule>>()
        subsItems.filter { it.enable }.forEach { subsItem ->
            val subsRaw = subsIdToRaw[subsItem.id] ?: return@forEach
            val subAppSubsConfigs = appSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subGroupSubsConfigs = groupSubsConfigs.filter { c -> c.subsItemId == subsItem.id }
            val subCategoryConfigs = categoryConfigs.filter { c -> c.subsItemId == subsItem.id }
            subsRaw.apps.filter { appRaw ->
                // 筛选 当前启用的 app 订阅规则
                (subAppSubsConfigs.find { c -> c.appId == appRaw.id }?.enable ?: true)
            }.forEach { appRaw ->
                val appGroupConfigs = subGroupSubsConfigs.filter { c -> c.appId == appRaw.id }
                val rules = appIdToRules[appRaw.id] ?: mutableListOf()
                appIdToRules[appRaw.id] = rules
                appRaw.groups.filter { groupRaw ->
                    getGroupRawEnable(
                        groupRaw,
                        appGroupConfigs,
                        subsRaw.groupToCategoryMap[groupRaw],
                        subCategoryConfigs
                    )
                }.filter { groupRaw ->
                    // 筛选合法选择器的规则组, 如果一个规则组内某个选择器语法错误, 则禁用/丢弃此规则组
                    groupRaw.valid
                }.forEach { groupRaw ->
                    val groupRuleList = mutableListOf<Rule>()
                    groupRaw.rules.forEachIndexed { ruleIndex, ruleRaw ->
                        val activityIds =
                            (ruleRaw.activityIds ?: groupRaw.activityIds ?: appRaw.activityIds
                            ?: emptyList()).map { activityId ->
                                if (activityId.startsWith('.')) { // .a.b.c -> com.x.y.x.a.b.c
                                    appRaw.id + activityId
                                } else {
                                    activityId
                                }
                            }.toSet()

                        val excludeActivityIds =
                            (ruleRaw.excludeActivityIds ?: groupRaw.excludeActivityIds
                            ?: appRaw.excludeActivityIds ?: emptyList()).map { activityId ->
                                if (activityId.startsWith('.')) { // .a.b.c -> com.x.y.x.a.b.c
                                    appRaw.id + activityId
                                } else {
                                    activityId
                                }
                            }.toSet()

                        val quickFind =
                            ruleRaw.quickFind ?: groupRaw.quickFind ?: appRaw.quickFind ?: false

                        val matchDelay =
                            ruleRaw.matchDelay ?: groupRaw.matchDelay ?: appRaw.matchDelay
                        val matchTime = ruleRaw.matchTime ?: groupRaw.matchTime ?: appRaw.matchTime
                        val resetMatch =
                            ruleRaw.resetMatch ?: groupRaw.resetMatch ?: appRaw.resetMatch
                        val actionDelay =
                            ruleRaw.actionDelay ?: groupRaw.actionDelay ?: appRaw.actionDelay ?: 0

                        groupRuleList.add(
                            Rule(
                                quickFind = quickFind,
                                actionDelay = actionDelay,
                                index = ruleIndex,
                                matches = ruleRaw.matches.map { Selector.parse(it) },
                                excludeMatches = (ruleRaw.excludeMatches ?: emptyList()).map {
                                    Selector.parse(
                                        it
                                    )
                                },
                                matchDelay = matchDelay,
                                matchTime = matchTime,
                                appId = appRaw.id,
                                activityIds = activityIds,
                                excludeActivityIds = excludeActivityIds,
                                key = ruleRaw.key,
                                preKeys = (ruleRaw.preKeys ?: emptyList()).toSet(),
                                rule = ruleRaw,
                                group = groupRaw,
                                app = appRaw,
                                subsItem = subsItem,
                                resetMatch = resetMatch,
                            )
                        )
                    }
                    groupRuleList.forEach { ruleConfig ->
                        // 保留原始对象引用, 方便判断 lastTriggerRule 时直接使用 ===
                        ruleConfig.preRules = groupRuleList.filter { otherRule ->
                            (otherRule.key != null) && ruleConfig.preKeys.contains(
                                otherRule.key
                            )
                        }.toSet()
                        // 共用次数
                        val maxKey = ruleConfig.rule.actionMaximumKey
                        if (maxKey != null) {
                            val otherRule = groupRuleList.find { r -> r.key == maxKey }
                            if (otherRule != null) {
                                ruleConfig.actionCount = otherRule.actionCount
                            }
                        }
                        // 共用 cd
                        val cdKey = ruleConfig.rule.actionCdKey
                        if (cdKey != null) {
                            val otherRule = groupRuleList.find { r -> r.key == cdKey }
                            if (otherRule != null) {
                                ruleConfig.actionTriggerTime = otherRule.actionTriggerTime
                            }
                        }
                    }
                    rules.addAll(groupRuleList)
                }
            }
        }
        appIdToRules.values.forEach { rules ->
            // 让开屏广告类规则全排在最前面
            rules.sortBy { r -> if (r.isOpenAd) 0 else 1 }
        }
        appIdToRules.filter { it.value.isNotEmpty() }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyMap<String, List<Rule>>())
}

data class AppRule(
    val visibleMap: Map<String, List<Rule>> = emptyMap(),
    val unVisibleMap: Map<String, List<Rule>> = emptyMap(),
    val allMap: Map<String, List<Rule>> = emptyMap(),
)

val appRuleFlow by lazy {
    combine(appIdToRulesFlow, appInfoCacheFlow) { appIdToRules, appInfoCache ->
        val visibleMap = mutableMapOf<String, List<Rule>>()
        val unVisibleMap = mutableMapOf<String, List<Rule>>()
        appIdToRules.forEach { (appId, rules) ->
            if (appInfoCache.containsKey(appId)) {
                visibleMap[appId] = rules
            } else {
                unVisibleMap[appId] = rules
            }
        }
        AppRule(
            visibleMap = visibleMap, unVisibleMap = unVisibleMap, allMap = appIdToRules
        )
    }.stateIn(appScope, SharingStarted.Eagerly, AppRule())
}


fun initSubsState() {
    subsItemsFlow.value
    subsIdToRawFlow.value
    appScope.launchTry {
        // reload subsRaw file by diff
        var oldSubsIdToMtime = emptyMap<Long, Long>()
        subsIdToMtimeFlow.collect { subsIdToMtime ->
            val commonMap = subsIdToMtime.filter { e -> oldSubsIdToMtime[e.key] == e.value }
            oldSubsIdToMtime = subsIdToMtime
            val oldSubsIdToRaw = subsIdToRawFlow.value
            subsIdToRawFlow.value = subsIdToMtime.map { entry ->
                if (commonMap.containsKey(entry.key)) {
                    entry.key to oldSubsIdToRaw[entry.key]
                } else {
                    val newSubsRaw =
                        withContext(Dispatchers.IO) { SubsItem.getSubscriptionRaw(entry.key) }
                    newSubsRaw?.apps?.apply {
                        updateAppInfo(*map { a -> a.id }.toTypedArray())
                    }
                    entry.key to newSubsRaw
                }
            }.toMap()
        }
    }
}