package li.gkd.app.ui.subscription

import li.gkd.app.resources.Res
import li.gkd.app.resources.action_continue
import li.gkd.app.resources.action_notice
import li.gkd.app.resources.global_rule_enable_builtin_warning
import li.gkd.app.resources.global_rule_enable_name_list_warning
import li.gkd.app.resources.global_rule_enable_similar_warning
import li.gkd.app.resources.global_rule_enable_title
import li.gkd.app.resources.global_rule_enable_batch_title
import li.gkd.app.resources.global_rule_enable_batch_apps
import li.gkd.app.resources.global_rule_enable_batch_groups
import li.gkd.app.resources.global_rule_enable_attention
import li.gkd.app.resources.global_rule_enable_risk
import li.gkd.app.resources.global_rule_enable_similar_risk
import li.gkd.app.resources.global_rule_enable_qualified_name
import li.gkd.app.resources.global_rule_similar_group_disabled
import li.gkd.app.resources.global_rule_similar_group_enabled
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchRequest
import li.gkd.app.rule.RuleSwitchResult
import li.gkd.app.ui.component.DialogRequests
import org.jetbrains.compose.resources.getString

enum class RuleSwitchHost {
    AppRules,
    GlobalRuleApps,
    RuleDetail,
}

suspend fun applyRuleSwitchWithConfirmation(
    request: RuleSwitchRequest,
    setting: RuleSetting,
    dialogs: DialogRequests,
    host: RuleSwitchHost,
    confirmation: String? = null,
): RuleSwitchResult? {
    if (!confirmRuleSwitch(request, setting, dialogs, host, confirmation)) return null
    return RuleGroupConfigService.apply(request, setting)
}

suspend fun confirmRuleSwitch(
    request: RuleSwitchRequest,
    setting: RuleSetting,
    dialogs: DialogRequests,
    host: RuleSwitchHost,
    confirmation: String? = null,
): Boolean {
    val warnings = if (setting == RuleSetting.Enabled) request.enableWarnings else emptyList()
    val batch = confirmation != null || warnings.size > 1
    val messages = buildList {
        if (setting == RuleSetting.Enabled && batch) {
            add(getString(
                if (host == RuleSwitchHost.AppRules) Res.string.global_rule_enable_batch_groups
                else Res.string.global_rule_enable_batch_apps,
                request.expected.size.toString(),
            ))
            if (warnings.isNotEmpty()) add(getString(Res.string.global_rule_enable_attention))
        } else {
            confirmation?.let { add(it) }
        }
        warnings.forEach { warning ->
            add(buildList {
                when (host) {
                    RuleSwitchHost.AppRules -> {
                        val subscription = request.subscriptions.getValue(warning.target.subsId)
                        val sameNameGroups = request.subscriptions.values.flatMap { subs ->
                            subs.globalGroups.filter { it.name == warning.groupName }.map { subs.id to it.key }
                        }
                        val name = if (sameNameGroups.size > 1) getString(
                            Res.string.global_rule_enable_qualified_name,
                            warning.groupName, subscription.name,
                        ) else warning.groupName
                        add(if (sameNameGroups.count { it.first == subscription.id } > 1)
                            getString(Res.string.global_rule_enable_qualified_name, name, warning.target.groupKey.toString())
                        else name)
                    }
                    RuleSwitchHost.GlobalRuleApps -> {
                        val ambiguous = warnings.any {
                            it.appName == warning.appName && it.target.appId != warning.target.appId
                        }
                        add(if (ambiguous) getString(
                            Res.string.global_rule_enable_qualified_name,
                            warning.appName, warning.target.appId,
                        ) else warning.appName)
                    }
                    RuleSwitchHost.RuleDetail -> Unit
                }
                if (warning.builtInDisabled) add(getString(
                    Res.string.global_rule_enable_builtin_warning,
                ))
                if (warning.similarGroups.isNotEmpty()) {
                    add(buildList {
                        add(getString(
                            if (warning.excludedByGroupName) Res.string.global_rule_enable_name_list_warning
                            else Res.string.global_rule_enable_similar_warning,
                        ))
                        warning.similarGroups.forEach {
                            add(getString(
                                if (it.enabled) Res.string.global_rule_similar_group_enabled
                                else Res.string.global_rule_similar_group_disabled,
                                it.name,
                            ))
                        }
                    }.joinToString("\n"))
                }
            }.joinToString("\n\n"))
        }
        if (warnings.isNotEmpty()) add(getString(
            if (warnings.any { it.similarGroups.isNotEmpty() }) Res.string.global_rule_enable_similar_risk
            else Res.string.global_rule_enable_risk,
        ))
    }
    return messages.isEmpty() || dialogs.confirm(
        title = getString(when {
            setting != RuleSetting.Enabled -> Res.string.action_notice
            batch -> Res.string.global_rule_enable_batch_title
            else -> Res.string.global_rule_enable_title
        }),
        text = messages.joinToString("\n\n"),
        confirmText = if (setting == RuleSetting.Enabled) getString(Res.string.action_continue) else null,
    )
}
