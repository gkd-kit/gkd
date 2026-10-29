package li.gkd.app.rule

import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

enum class RuleStatus(val descriptionResource: StringResource?) {
    Ready(null),
    ActionLimitReached(Res.string.rule_status_maximum_actions),
    PrerequisitePending(Res.string.rule_status_prerequisite),
    MatchDelay(Res.string.rule_status_match_delay),
    MatchTimeout(Res.string.rule_status_match_timeout),
    Cooldown(Res.string.rule_status_cooldown),
    ActionDelay(Res.string.rule_status_action_delay);

    val ok: Boolean
        get() = this == Ready

    val alive: Boolean
        get() = this != ActionLimitReached && this != PrerequisitePending && this != MatchTimeout
}

/** Shared production gate, with no diagnostic snapshots or strings on the normal path. */
fun evaluateRuleStatus(
    count: Int,
    maximum: Int?,
    prerequisite: Boolean,
    time: Long,
    changed: Long,
    matchDelay: Long,
    matchTime: Long?,
    trigger: Long,
    cooldown: Long,
    delay: Long,
    actionDelay: Long,
): RuleStatus =
    when {
        maximum != null && count >= maximum -> RuleStatus.ActionLimitReached
        !prerequisite -> RuleStatus.PrerequisitePending
        matchDelay > 0 && time - changed < matchDelay -> RuleStatus.MatchDelay
        matchTime != null && time - changed > matchTime + matchDelay -> RuleStatus.MatchTimeout
        trigger + cooldown > time -> RuleStatus.Cooldown
        delay > 0 && delay + actionDelay > time -> RuleStatus.ActionDelay
        else -> RuleStatus.Ready
    }
