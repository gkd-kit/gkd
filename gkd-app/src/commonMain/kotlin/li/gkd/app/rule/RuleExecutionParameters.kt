package li.gkd.app.rule

import li.gkd.app.subscription.RawSubscription
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector

class RuleExecutionParameters(
    rule: RawSubscription.RawRuleProps,
    private val group: RawSubscription.RawGroupProps
) {
    val preKeys = (rule.preKeys ?: emptyList()).toSet()
    val matches =
        (rule.matches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.compile(s).value }
    val anyMatches =
        (rule.anyMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.compile(s).value }
    val excludeMatches =
        (rule.excludeMatches ?: emptyList()).map { s ->
            group.cacheMap[s] ?: Selector.compile(s).value
        }
    val excludeAllMatches =
        (rule.excludeAllMatches ?: emptyList()).map { s ->
            group.cacheMap[s] ?: Selector.compile(s).value
        }

    val resetMatch = rule.resetMatch ?: group.resetMatch
    val matchDelay = rule.matchDelay ?: group.matchDelay ?: 0L
    val actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L
    val matchTime = rule.matchTime ?: group.matchTime
    val forcedTime = rule.forcedTime ?: group.forcedTime ?: 0L
    val matchOptions = MatchOptions(
        fastQuery = rule.fastQuery ?: group.fastQuery ?: false
    )
    val matchRoot = rule.matchRoot ?: group.matchRoot ?: false
    val order = rule.order ?: group.order ?: 0

    val actionCdKey = rule.actionCdKey ?: group.actionCdKey
    val actionCd = rule.actionCd ?: if (actionCdKey != null) {
        group.rules.find { r -> r.key == actionCdKey }?.actionCd
    } else {
        null
    } ?: group.actionCd ?: 1000L

    val actionMaximumKey = rule.actionMaximumKey ?: group.actionMaximumKey
    val actionMaximum = rule.actionMaximum ?: if (actionMaximumKey != null) {
        group.rules.find { r -> r.key == actionMaximumKey }?.actionMaximum
    } else {
        null
    } ?: group.actionMaximum

    val priorityTime = rule.priorityTime ?: group.priorityTime ?: 0
    val priorityActionMaximum = rule.priorityActionMaximum ?: group.priorityActionMaximum ?: 1
}
