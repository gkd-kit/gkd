package li.songe.gkd.util

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.Rule
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.selector.Selector

val subsItemsFlow by lazy {
    DbSet.subsItemDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

private val subsIdToMtimeFlow by lazy {
    DbSet.subsItemDao.query().map { it.sortedBy { s -> s.id }.associate { s -> s.id to s.mtime } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val subsIdToRawFlow by lazy {
    subsIdToMtimeFlow.map { subsIdToMtime ->
        subsIdToMtime.map { entry ->
            entry.key to SubsItem.getSubscriptionRaw(entry.key)
        }.toMap()
    }.onEach { rawMap ->
        updateAppInfo(*rawMap.values.map { subsRaw ->
            subsRaw?.apps?.map { a -> a.id }
        }.flatMap { it ?: emptyList() }.toTypedArray())
    }.stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val subsConfigsFlow by lazy {
    DbSet.subsConfigDao.query().stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

val appIdToRulesFlow by lazy {
    combine(subsItemsFlow,
        subsIdToRawFlow,
        subsConfigsFlow,
        appInfoCacheFlow,
        storeFlow.map(appScope) { s -> s.enableGroup }) { subsItems, subsIdToRaw, subsConfigs, appInfoCache, enableGroup ->
        val appSubsConfigs = subsConfigs.filter { it.type == SubsConfig.AppType }
        val groupSubsConfigs = subsConfigs.filter { it.type == SubsConfig.GroupType }
        val appIdToRules = mutableMapOf<String, MutableList<Rule>>()
        subsItems.filter { it.enable }.forEach { subsItem ->
            (subsIdToRaw[subsItem.id]?.apps ?: emptyList()).filter { appRaw ->
                // 筛选 已经安装的 APP 和 当前启用的 app 订阅规则
                appInfoCache.containsKey(appRaw.id) && (appSubsConfigs.find { subsConfig ->
                    subsConfig.subsItemId == subsItem.id && subsConfig.appId == appRaw.id
                }?.enable ?: true)
            }.forEach { appRaw ->
                val rules = appIdToRules[appRaw.id] ?: mutableListOf()
                appIdToRules[appRaw.id] = rules
                appRaw.groups.filter { groupRaw ->
                    // 筛选已经启用的规则组
                    groupSubsConfigs.find { subsConfig ->
                        subsConfig.subsItemId == subsItem.id && subsConfig.appId == appRaw.id && subsConfig.groupKey == groupRaw.key
                    }?.enable ?: enableGroup ?: groupRaw.enable ?: true
                }.filter { groupRaw ->
                    // 筛选合法选择器的规则组, 如果一个规则组内某个选择器语法错误, 则禁用/丢弃此规则组
                    groupRaw.valid
                }.forEach { groupRaw ->
                    val ruleGroupList = mutableListOf<Rule>()
                    groupRaw.rules.forEachIndexed { ruleIndex, ruleRaw ->
                        val actionCd = Rule.defaultMiniCd.coerceAtLeast(
                            ruleRaw.actionCd ?: groupRaw.actionCd ?: appRaw.actionCd
                            ?: Rule.defaultMiniCd
                        )
                        val delay =
                            ruleRaw.actionDelay ?: groupRaw.actionDelay ?: appRaw.actionDelay ?: 0
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

                        val matchLauncher =
                            ruleRaw.matchLauncher ?: groupRaw.matchLauncher ?: appRaw.matchLauncher
                            ?: false
                        val quickFind =
                            ruleRaw.quickFind ?: groupRaw.quickFind ?: appRaw.quickFind ?: false

                        val matchDelay =
                            ruleRaw.matchDelay ?: groupRaw.matchDelay ?: appRaw.matchDelay
                        val matchTime = ruleRaw.matchTime ?: groupRaw.matchTime ?: appRaw.matchTime
                        val actionMaximum =
                            ruleRaw.actionMaximum ?: groupRaw.actionMaximum ?: appRaw.actionMaximum

                        ruleGroupList.add(
                            Rule(
                                matchLauncher = matchLauncher,
                                quickFind = quickFind,
                                actionCd = actionCd,
                                actionDelay = delay,
                                index = ruleIndex,
                                matches = ruleRaw.matches.map { Selector.parse(it) },
                                excludeMatches = ruleRaw.excludeMatches.map {
                                    Selector.parse(
                                        it
                                    )
                                },
                                matchDelay = matchDelay,
                                matchTime = matchTime,
                                actionMaximum = actionMaximum,
                                appId = appRaw.id,
                                activityIds = activityIds,
                                excludeActivityIds = excludeActivityIds,
                                key = ruleRaw.key,
                                preKeys = ruleRaw.preKeys.toSet(),
                                rule = ruleRaw,
                                group = groupRaw,
                                app = appRaw,
                                subsItem = subsItem
                            )
                        )
                    }
                    ruleGroupList.forEachIndexed { index, ruleConfig ->
                        ruleGroupList[index] = ruleConfig.copy(
                            preRules = ruleGroupList.filter {
                                (it.key != null) && ruleConfig.preKeys.contains(
                                    it.key
                                )
                            }.toSet()
                        )
                    }
                    rules.addAll(ruleGroupList)
                }
            }
        }
        appIdToRules.filter { it.value.isNotEmpty() }
    }.stateIn<Map<String, List<Rule>>>(appScope, SharingStarted.Eagerly, emptyMap())
}


fun initSubsState() {
    subsItemsFlow.value
    subsIdToMtimeFlow.value
    subsIdToRawFlow.value
    subsConfigsFlow.value
}