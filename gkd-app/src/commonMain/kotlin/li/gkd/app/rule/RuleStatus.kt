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

    val ok: Boolean get() = this == Ready
    val alive: Boolean
        get() = this != ActionLimitReached && this != PrerequisitePending && this != MatchTimeout
}
