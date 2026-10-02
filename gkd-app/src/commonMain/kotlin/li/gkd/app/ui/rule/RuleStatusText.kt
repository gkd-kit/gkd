package li.gkd.app.ui.rule

import li.gkd.app.resources.Res
import li.gkd.app.resources.rule_status_action_delay
import li.gkd.app.resources.rule_status_cooldown
import li.gkd.app.resources.rule_status_match_delay
import li.gkd.app.resources.rule_status_match_timeout
import li.gkd.app.resources.rule_status_maximum_actions
import li.gkd.app.resources.rule_status_prerequisite
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleStatus
import li.gkd.app.ui.text.getSync

fun ResolvedRule.statusText(): String {
    val description = when (status) {
        RuleStatus.Ready -> "ok"
        RuleStatus.ActionLimitReached -> Res.string.rule_status_maximum_actions.getSync()
        RuleStatus.PrerequisitePending -> Res.string.rule_status_prerequisite.getSync()
        RuleStatus.MatchDelay -> Res.string.rule_status_match_delay.getSync()
        RuleStatus.MatchTimeout -> Res.string.rule_status_match_timeout.getSync()
        RuleStatus.Cooldown -> Res.string.rule_status_cooldown.getSync()
        RuleStatus.ActionDelay -> Res.string.rule_status_action_delay.getSync()
    }
    return "id:${subsItem.id}, v:${rawSubs.version}, type:${type}, gKey=${g.group.key}, gName:${g.group.name}, index:${index}, key:${key}, status:$description"
}
