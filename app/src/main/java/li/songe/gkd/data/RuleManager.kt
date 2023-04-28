package li.songe.gkd.data

import li.songe.selector.GkdSelector

class RuleManager(vararg subscriptionRawArray: SubscriptionRaw) {

    private data class TriggerRecord(val ctime: Long = System.currentTimeMillis(), val rule: Rule)

    private var count: Int = 0
        get() {
            field++
            return field
        }

    private val appToRulesMap = mutableMapOf<String, MutableList<Rule>>()

    init {
        subscriptionRawArray.forEach { subscriptionRaw ->
            subscriptionRaw.apps.forEach { appRaw ->
                val ruleConfigList = appToRulesMap[appRaw.id] ?: mutableListOf()
                appToRulesMap[appRaw.id] = ruleConfigList
                appRaw.groups.forEach { groupRaw ->
                    val ruleGroupList = mutableListOf<Rule>()
                    groupRaw.rules.forEach ruleEach@{ ruleRaw ->
                        if (ruleRaw.matches.isEmpty()) return@ruleEach
                        val cd = Rule.defaultMiniCd.coerceAtLeast(
                            ruleRaw.cd ?: groupRaw.cd ?: appRaw.cd ?: Rule.defaultMiniCd
                        )
                        val activityIds =
                            (ruleRaw.activityIds ?: groupRaw.activityIds ?: appRaw.activityIds
                            ?: listOf("*")).map { activityId ->
                                if (activityId.startsWith('.')) {
//                                    .a.b.c -> com.x.y.x.a.b.c
                                    return@map appRaw.id + activityId
                                }
                                activityId
                            }.toSet()


                        val excludeActivityIds =
                            (ruleRaw.excludeActivityIds ?: groupRaw.excludeActivityIds
                            ?: appRaw.excludeActivityIds
                            ?: emptyList()).toSet()

                        ruleGroupList.add(
                            Rule(
                                cd = cd,
                                index = count,
                                matches = ruleRaw.matches.map { GkdSelector.gkdSelectorParser(it) },
                                excludeMatches = ruleRaw.excludeMatches.map {
                                    GkdSelector.gkdSelectorParser(
                                        it
                                    )
                                },
                                appId = appRaw.id,
                                activityIds = activityIds,
                                excludeActivityIds = excludeActivityIds,
                                key = ruleRaw.key,
                                preKeys = ruleRaw.preKeys.toSet(),
                            )
                        )
                    }
                    ruleGroupList.forEachIndexed { index, ruleConfig ->
                        ruleGroupList[index] = ruleConfig.copy(
                            preRules = ruleGroupList.filter {
                                it.key != null && it.preKeys.contains(
                                    it.key
                                )
                            }.toSet()
                        )
                    }
                    ruleConfigList.addAll(ruleGroupList)
                }
            }
        }
    }


    private val triggerLogQueue = ArrayDeque<TriggerRecord>()


    fun trigger(rule: Rule) {
        rule.trigger()
        triggerLogQueue.addLast(TriggerRecord(rule = rule))
        while (triggerLogQueue.size >= 256) {
            triggerLogQueue.removeFirst()
        }
    }

    fun match(appId: String? = null, activityId: String? = null) = sequence {
        if (appId == null) return@sequence
        val rules = appToRulesMap[appId] ?: return@sequence
        rules.forEach { rule ->
            if (!rule.active) return@forEach // 处于冷却时间

            if (rule.excludeActivityIds.contains(activityId)) return@forEach // 是被排除的 界面 id

            if (rule.preRules.isNotEmpty()) { // 需要提前触发某个规则
                val record = triggerLogQueue.lastOrNull() ?: return@forEach
                if (!rule.preRules.any { it == record.rule }) return@forEach // 上一个触发的规则不在当前需要触发的列表
            }

            if (activityId == null || rule.matchAnyActivity  // 全匹配
                || rule.activityIds.contains(activityId) // 在匹配列表
            ) {
                yield(rule)
            }
        }
    }
}