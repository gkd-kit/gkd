package li.gkd.app.rule

import li.gkd.app.subscription.RuleValidationError

sealed interface RuleRestriction {
    data class Invalid(val error: RuleValidationError?) : RuleRestriction
    data object AppNotApplicable : RuleRestriction
    data object SubscriptionDisabled : RuleRestriction
    data object SubscriptionAppDisabled : RuleRestriction
    data object GlobalGroupDisabled : RuleRestriction
    data object ShadowedByAppRule : RuleRestriction
    data object AppExcluded : RuleRestriction
    data object VersionMismatch : RuleRestriction
}

enum class RuleControlScope { Group, CurrentApp, SubscriptionApp }
