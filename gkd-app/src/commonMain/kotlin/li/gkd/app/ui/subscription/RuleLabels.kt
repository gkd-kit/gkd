package li.gkd.app.ui.subscription

import androidx.compose.runtime.Composable
import li.gkd.app.resources.Res
import li.gkd.app.resources.category_disabled
import li.gkd.app.resources.category_disabled_description
import li.gkd.app.resources.category_enabled
import li.gkd.app.resources.category_enabled_description
import li.gkd.app.resources.category_follow_subscription
import li.gkd.app.resources.category_follow_subscription_description
import li.gkd.app.resources.category_settings
import li.gkd.app.resources.category_subscription_default
import li.gkd.app.resources.category_use_group_default
import li.gkd.app.resources.category_use_group_default_description
import li.gkd.app.resources.installed_apps_default_enabled
import li.gkd.app.resources.rule_builtin_app_scope
import li.gkd.app.resources.rule_current_app_scope
import li.gkd.app.resources.rule_custom_setting
import li.gkd.app.resources.rule_group_default
import li.gkd.app.resources.rule_group_scope
import li.gkd.app.resources.rule_invalid
import li.gkd.app.resources.rule_switch_changed_counts
import li.gkd.app.resources.rule_switch_restricted_count_suffix
import li.gkd.app.resources.rule_switch_skipped_count
import li.gkd.app.resources.setting_follow_default
import li.gkd.app.resources.setting_manual_disabled
import li.gkd.app.resources.setting_manual_enabled
import li.gkd.app.resources.subscription_app_switch_scope
import li.gkd.app.rule.CategorySetting
import li.gkd.app.rule.RuleControlScope
import li.gkd.app.rule.RuleEnableSource
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchResult
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val CategorySetting.labelResource: StringResource
    get() = when (this) {
        CategorySetting.FollowSubscription -> Res.string.category_follow_subscription
        CategorySetting.Enabled -> Res.string.category_enabled
        CategorySetting.Disabled -> Res.string.category_disabled
        CategorySetting.GroupDefault -> Res.string.category_use_group_default
    }

val CategorySetting.label: String
    @Composable get() = stringResource(labelResource)

suspend fun CategorySetting.labelText(): String = getString(labelResource)

private val RuleSetting.labelResource: StringResource
    get() = when (this) {
        RuleSetting.FollowDefault -> Res.string.setting_follow_default
        RuleSetting.Enabled -> Res.string.setting_manual_enabled
        RuleSetting.Disabled -> Res.string.setting_manual_disabled
    }

val RuleSetting.label: String
    @Composable get() = stringResource(labelResource)

suspend fun RuleSetting.labelText(): String = getString(labelResource)

private val RuleEnableSource.labelResource: StringResource
    get() = when (this) {
        RuleEnableSource.Manual -> Res.string.rule_custom_setting
        RuleEnableSource.Category -> Res.string.category_settings
        RuleEnableSource.SubscriptionCategory -> Res.string.category_subscription_default
        RuleEnableSource.GroupDefault -> Res.string.rule_group_default
        RuleEnableSource.Invalid -> Res.string.rule_invalid
        RuleEnableSource.BuiltInAppScope -> Res.string.rule_builtin_app_scope
        RuleEnableSource.InstalledApp -> Res.string.installed_apps_default_enabled
    }

val RuleEnableSource.label: String
    @Composable get() = stringResource(labelResource)

suspend fun RuleEnableSource.labelText(): String = getString(labelResource)

private val RuleControlScope.labelResource: StringResource
    get() = when (this) {
        RuleControlScope.Group -> Res.string.rule_group_scope
        RuleControlScope.CurrentApp -> Res.string.rule_current_app_scope
        RuleControlScope.SubscriptionApp -> Res.string.subscription_app_switch_scope
    }

val RuleControlScope.label: String
    @Composable get() = stringResource(labelResource)

suspend fun RuleControlScope.labelText(): String = getString(labelResource)

val CategorySetting.description: String
    @Composable get() = stringResource(when (this) {
        CategorySetting.FollowSubscription -> Res.string.category_follow_subscription_description
        CategorySetting.Enabled -> Res.string.category_enabled_description
        CategorySetting.Disabled -> Res.string.category_disabled_description
        CategorySetting.GroupDefault -> Res.string.category_use_group_default_description
    })

suspend fun RuleSwitchResult.failureMessage(): String? = if (invalid > 0)
    getString(Res.string.rule_switch_skipped_count, invalid.toString()) else null

suspend fun RuleSwitchResult.description(): String =
    getString(Res.string.rule_switch_changed_counts, changed.toString(), unchanged.toString()) +
        (failureMessage()?.let { "，$it" } ?: "") +
        (if (restricted > 0) getString(Res.string.rule_switch_restricted_count_suffix, restricted.toString()) else "")
