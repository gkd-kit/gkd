package li.gkd.app.rule

enum class RuleStatus {
    Ready, ActionLimitReached, PrerequisitePending, MatchDelay, MatchTimeout, Cooldown, ActionDelay;

    val ok: Boolean get() = this == Ready
    val alive: Boolean
        get() = this != ActionLimitReached && this != PrerequisitePending && this != MatchTimeout
}
