package li.songe.gkd.data

import li.songe.selector.Selector

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
                                matches = ruleRaw.matches.map { Selector.parse(it) },
                                excludeMatches = ruleRaw.excludeMatches.map {
                                    Selector.parse(
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
        if (activityId == null) {
            yieldAll(rules)
            return@sequence
        }
        rules.forEach { rule ->
            if (rule.excludeActivityIds.any { activityId.startsWith(it) }) return@forEach // 是被排除的 界面 id

            if (rule.matchAnyActivity || rule.activityIds.any { activityId.startsWith(it) } // 在匹配列表
            ) {
                yield(rule)
            }
        }
    }

    fun ruleIsAvailable(rule: Rule): Boolean {
        if (!rule.active) return false // 处于冷却时间
        if (rule.preKeys.isNotEmpty()) { // 需要提前触发某个规则
            if (rule.preRules.isEmpty()) return false // 声明了 preKeys 但是没有在当前列表找到
            val record = triggerLogQueue.lastOrNull() ?: return false
            if (!rule.preRules.any { it == record.rule }) return false // 上一个触发的规则不在当前需要触发的列表
        }
        return true
    }
}