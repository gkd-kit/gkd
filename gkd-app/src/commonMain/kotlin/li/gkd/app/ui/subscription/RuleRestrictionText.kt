package li.gkd.app.ui.subscription

import androidx.compose.runtime.Composable
import li.gkd.app.resources.Res
import li.gkd.app.resources.global_rule_group_disabled
import li.gkd.app.resources.global_rule_shadowed_by_app_rule
import li.gkd.app.resources.rule_app_excluded
import li.gkd.app.resources.rule_app_not_applicable
import li.gkd.app.resources.rule_app_version_mismatch
import li.gkd.app.resources.rule_invalid_cannot_enable
import li.gkd.app.resources.rule_invalid_position
import li.gkd.app.resources.selector_invalid_detail
import li.gkd.app.resources.subscription_app_disabled
import li.gkd.app.resources.subscription_disabled
import li.gkd.app.rule.RuleRestriction
import li.gkd.app.subscription.RuleValidationError
import org.jetbrains.compose.resources.stringResource

val RuleRestriction.label: String
    @Composable get() = when (this) {
        is RuleRestriction.Invalid -> when (val failure = error) {
            null -> stringResource(Res.string.rule_invalid_cannot_enable)
            is RuleValidationError.SelectorError -> stringResource(Res.string.selector_invalid_detail, failure.source, failure.detail)
            is RuleValidationError.InvalidPosition -> stringResource(Res.string.rule_invalid_position, failure.value)
        }
        RuleRestriction.AppNotApplicable -> stringResource(Res.string.rule_app_not_applicable)
        RuleRestriction.SubscriptionDisabled -> stringResource(Res.string.subscription_disabled)
        RuleRestriction.SubscriptionAppDisabled -> stringResource(Res.string.subscription_app_disabled)
        RuleRestriction.GlobalGroupDisabled -> stringResource(Res.string.global_rule_group_disabled)
        RuleRestriction.ShadowedByAppRule -> stringResource(Res.string.global_rule_shadowed_by_app_rule)
        RuleRestriction.AppExcluded -> stringResource(Res.string.rule_app_excluded)
        RuleRestriction.VersionMismatch -> stringResource(Res.string.rule_app_version_mismatch)
    }
