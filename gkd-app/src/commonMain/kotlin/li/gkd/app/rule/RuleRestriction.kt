package li.gkd.app.rule

import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*
import li.gkd.app.subscription.RuleValidationError
import org.jetbrains.compose.resources.StringResource

sealed interface RuleRestriction {
    val message: ErrorMessage
        get() = when (this) {
            is Invalid -> error?.message ?: ErrorMessage.Resource(Res.string.rule_invalid_cannot_enable)
            AppNotApplicable -> ErrorMessage.Resource(Res.string.rule_app_not_applicable)
            SubscriptionDisabled -> ErrorMessage.Resource(Res.string.subscription_disabled)
            SubscriptionAppDisabled -> ErrorMessage.Resource(Res.string.subscription_app_disabled)
            GlobalGroupDisabled -> ErrorMessage.Resource(Res.string.global_rule_group_disabled)
            ShadowedByAppRule -> ErrorMessage.Resource(Res.string.global_rule_shadowed_by_app_rule)
            AppExcluded -> ErrorMessage.Resource(Res.string.rule_app_excluded)
            VersionMismatch -> ErrorMessage.Resource(Res.string.rule_app_version_mismatch)
        }

    data class Invalid(val error: RuleValidationError?) : RuleRestriction
    data object AppNotApplicable : RuleRestriction
    data object SubscriptionDisabled : RuleRestriction
    data object SubscriptionAppDisabled : RuleRestriction
    data object GlobalGroupDisabled : RuleRestriction
    data object ShadowedByAppRule : RuleRestriction
    data object AppExcluded : RuleRestriction
    data object VersionMismatch : RuleRestriction
}

enum class RuleControlScope(val labelResource: StringResource) {
    Group(Res.string.rule_group_scope),
    CurrentApp(Res.string.rule_current_app_scope),
    SubscriptionApp(Res.string.subscription_app_switch_scope),
}
