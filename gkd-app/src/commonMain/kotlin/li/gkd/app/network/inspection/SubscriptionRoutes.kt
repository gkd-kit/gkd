package li.gkd.app.network.inspection

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import li.gkd.app.rule.CategorySetting
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.subscription.SubscriptionInputParser
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.edit
import li.gkd.db.RuleGroupType
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore
import li.gkd.db.SubscriptionIds

fun Route.registerSubscriptionRoutes() {
    post("/getRules") {
        val data = call.receive<SubscriptionRequest>()
        requireSupportedSubscription(data.subsId)
        val result = SubscriptionRepository.withSubscription(data.subsId) { subscription ->
            val configuration = SubscriptionConfigStore.capture()
            GetRulesResponse(
                subscription,
                configuration.copy(
                    subsItems = configuration.subsItems.filter { it.id == data.subsId },
                    appConfigs = configuration.appConfigs.filter { it.subsId == data.subsId },
                    categoryConfigs = configuration.categoryConfigs.filter { it.subsId == data.subsId },
                    appGroupConfigs = configuration.appGroupConfigs.filter { it.subsId == data.subsId },
                    globalGroupConfigs = configuration.globalGroupConfigs.filter { it.subsId == data.subsId },
                ),
            )
        }
        call.respond(result)
    }
    post("/appendRules") {
        val data = call.receive<AppendRulesRequest>()
        requireSupportedSubscription(data.subsId)
        val target = RuleTarget(data.groupType, data.appId, 0).contentTarget(data.subsId)
        val input = SubscriptionInputParser.parse(data.source)
        when (target) {
            is RuleGroupTarget.App -> {
                val groups = input.parseAppGroups(target.appId)
                var added = emptyList<RawSubscription.RawAppGroup>()
                SubscriptionRepository.update(data.subsId) { subscription ->
                    subscription.edit {
                        added = appendAppGroups(subscription.getApp(target.appId), groups)
                            .groups.takeLast(groups.size)
                    }
                }
                call.respond(added)
            }
            is RuleGroupTarget.Global -> {
                val group = input.parseGlobalGroup()
                lateinit var added: RawSubscription.RawGlobalGroup
                SubscriptionRepository.update(data.subsId) { subscription ->
                    subscription.edit { added = appendGlobalGroup(group) }
                }
                call.respond(listOf(added))
            }
        }
    }
    post("/replaceRuleGroup") {
        val data = call.receive<ReplaceRuleGroupRequest>()
        requireSupportedSubscription(data.subsId)
        val target = data.target.contentTarget(data.subsId)
        val input = SubscriptionInputParser.parse(data.source, target.groupKey)
        val changed = when (target) {
            is RuleGroupTarget.App -> {
                val groups = input.parseAppGroups(target.appId)
                if (groups.size != 1) throw BadRequestException("Expected one rule group")
                val group = groups.single().copy(key = target.groupKey)
                SubscriptionRepository.update(data.subsId) { subscription ->
                    val app = subscription.apps.find { it.id == target.appId }
                    val current = app?.groups?.find { it.key == target.groupKey }
                        ?: throw SubscriptionException(SubscriptionFailureReason.RuleMissing)
                    if (group == current) subscription else subscription.edit {
                        replaceAppGroup(app, target.groupKey, current, group)
                    }
                }
            }
            is RuleGroupTarget.Global -> {
                val group = input.parseGlobalGroup().copy(key = target.groupKey)
                SubscriptionRepository.update(data.subsId) { subscription ->
                    val current = subscription.globalGroups.find { it.key == target.groupKey }
                        ?: throw SubscriptionException(SubscriptionFailureReason.RuleMissing)
                    if (group == current) subscription else subscription.edit {
                        replaceGlobalGroup(target.groupKey, current, group)
                    }
                }
            }
        }
        call.respond(changed)
    }
    post("/deleteRuleGroup") {
        val data = call.receive<RuleGroupRequest>()
        requireSupportedSubscription(data.subsId)
        val target = data.target.contentTarget(data.subsId)
        var deleted = 0
        SubscriptionRepository.update(data.subsId) { subscription ->
            subscription.edit {
                deleted = when (target) {
                    is RuleGroupTarget.App -> removeAppGroups(target.appId, removeAppIfEmpty = true) {
                        it.key == target.groupKey
                    }.size
                    is RuleGroupTarget.Global -> removeGlobalGroups { it.key == target.groupKey }.size
                }
            }
        }
        call.respond(deleted)
    }
    post("/setRuleGroupSetting") {
        val data = call.receive<GroupSettingRequest>()
        requireSupportedSubscription(data.subsId)
        call.respond(RuleGroupConfigService.setSetting(data.target.switchTarget(data.subsId), RuleSetting.from(data.enable)))
    }
    post("/setSubscriptionAppSetting") {
        val data = call.receive<AppSettingRequest>()
        requireSupportedSubscription(data.subsId)
        requireAppId(data.appId)
        call.respond(RuleGroupConfigService.setSetting(RuleSwitchTarget.App(data.subsId, data.appId), RuleSetting.from(data.enable)))
    }
    post("/setCategorySetting") {
        val data = call.receive<CategorySettingRequest>()
        requireSupportedSubscription(data.subsId)
        val setting = data.config?.let { CategorySetting.fromOverride(it.enable) }
            ?: CategorySetting.FollowSubscription
        RuleGroupConfigService.setCategorySetting(data.subsId, data.categoryKey, setting)
        call.respond(rpcSuccess)
    }
    post("/setSubscriptionEnabled") {
        val data = call.receive<SubscriptionEnabledRequest>()
        requireSupportedSubscription(data.subsId)
        SubscriptionRepository.requestEnabled(data.subsId, data.enabled)
        call.respond(rpcSuccess)
    }
}

private fun requireSupportedSubscription(subsId: Long) {
    if (!SubscriptionIds.isLocal(subsId)) throw BadRequestException("Unsupported subscription ID")
}

private fun requireAppId(appId: String) {
    if (appId.isBlank()) throw BadRequestException("Expected an application ID")
}

@Serializable
private data class RuleTarget(val groupType: Int, val appId: String?, val groupKey: Int) {
    fun contentTarget(subsId: Long): RuleGroupTarget = when (groupType) {
        RuleGroupType.App -> RuleGroupTarget.App(subsId, requireNotNullAppId(), groupKey)
        RuleGroupType.Global -> {
            if (appId != null) throw BadRequestException("Expected a global rule group without an application ID")
            RuleGroupTarget.Global(subsId, groupKey)
        }
        else -> throw BadRequestException("Unknown rule group type")
    }

    fun switchTarget(subsId: Long): RuleSwitchTarget = when (groupType) {
        RuleGroupType.App -> RuleSwitchTarget.AppGroup(subsId, requireNotNullAppId(), groupKey)
        RuleGroupType.Global -> if (appId == null) RuleSwitchTarget.GlobalGroup(subsId, groupKey)
            else RuleSwitchTarget.GlobalApp(subsId, groupKey, requireNotNullAppId())
        else -> throw BadRequestException("Unknown rule group type")
    }

    private fun requireNotNullAppId(): String {
        val value = appId ?: throw BadRequestException("Expected an application ID")
        requireAppId(value)
        return value
    }
}

@Serializable
private data class SubscriptionRequest(val subsId: Long)

@Serializable
private data class GetRulesResponse(val subscription: RawSubscription, val configuration: SubscriptionConfigSnapshot)

@Serializable
private data class AppendRulesRequest(val subsId: Long, val groupType: Int, val appId: String?, val source: String)

@Serializable
private data class ReplaceRuleGroupRequest(val subsId: Long, val target: RuleTarget, val source: String)

@Serializable
private data class RuleGroupRequest(val subsId: Long, val target: RuleTarget)

@Serializable
private data class GroupSettingRequest(val subsId: Long, val target: RuleTarget, val enable: Boolean?)

@Serializable
private data class AppSettingRequest(val subsId: Long, val appId: String, val enable: Boolean?)

@Serializable
private data class CategoryOverride(val enable: Boolean?)

@Serializable
private data class CategorySettingRequest(val subsId: Long, val categoryKey: Int, val config: CategoryOverride?)

@Serializable
private data class SubscriptionEnabledRequest(val subsId: Long, val enabled: Boolean)
