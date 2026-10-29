package li.gkd.app.rule

import li.gkd.app.subscription.RawSubscription
import li.gkd.selector.MatchOptions

/** Effective execution configuration; compiled selectors and mutable state live separately. */
@ConsistentCopyVisibility
data class RuleExecutionParameters
private constructor(
    val matches: List<String>,
    val anyMatches: List<String>,
    val excludeMatches: List<String>,
    val excludeAllMatches: List<String>,
    val action: String?,
    override val position: RawSubscription.Position?,
    override val swipeArg: RawSubscription.SwipeArg?,
    val preKeys: Set<Int>,
    val resetMatchType: ResetMatchType,
    val matchDelay: Long,
    val actionDelay: Long,
    val matchTime: Long?,
    val forcedTime: Long,
    val matchOptions: MatchOptions,
    val matchRoot: Boolean,
    val order: Int,
    val actionCdKey: Int?,
    val actionCd: Long,
    val actionMaximumKey: Int?,
    val actionMaximum: Int?,
    val priorityTime: Long,
    val priorityActionMaximum: Int,
) : RawSubscription.LocationProps {
    companion object {
        fun resolve(
            rule: RawSubscription.RawRuleProps,
            group: RawSubscription.RawGroupProps,
        ): RuleExecutionParameters {
            val actionCdKey = rule.actionCdKey ?: group.actionCdKey
            val actionMaximumKey = rule.actionMaximumKey ?: group.actionMaximumKey
            val resetMatch = rule.resetMatch ?: group.resetMatch
            return RuleExecutionParameters(
                matches = rule.matches.orEmpty(),
                anyMatches = rule.anyMatches.orEmpty(),
                excludeMatches = rule.excludeMatches.orEmpty(),
                excludeAllMatches = rule.excludeAllMatches.orEmpty(),
                action = rule.action,
                position = rule.position,
                swipeArg = rule.swipeArg,
                preKeys = rule.preKeys.orEmpty().toSet(),
                resetMatchType =
                    ResetMatchType.allSubObject.find { it.value == resetMatch }
                        ?: ResetMatchType.Activity,
                matchDelay = rule.matchDelay ?: group.matchDelay ?: 0L,
                actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L,
                matchTime = rule.matchTime ?: group.matchTime,
                forcedTime = rule.forcedTime ?: group.forcedTime ?: 0L,
                matchOptions = MatchOptions(fastQuery = rule.fastQuery ?: group.fastQuery ?: false),
                matchRoot = rule.matchRoot ?: group.matchRoot ?: false,
                order = rule.order ?: group.order ?: 0,
                actionCdKey = actionCdKey,
                actionCd =
                    rule.actionCd
                        ?: actionCdKey?.let { key ->
                            group.rules.find { it.key == key }?.actionCd
                        }
                        ?: group.actionCd
                        ?: 1000L,
                actionMaximumKey = actionMaximumKey,
                actionMaximum =
                    rule.actionMaximum
                        ?: actionMaximumKey?.let { key ->
                            group.rules.find { it.key == key }?.actionMaximum
                        }
                        ?: group.actionMaximum,
                priorityTime = rule.priorityTime ?: group.priorityTime ?: 0L,
                priorityActionMaximum =
                    rule.priorityActionMaximum ?: group.priorityActionMaximum ?: 1,
            )
        }
    }
}
