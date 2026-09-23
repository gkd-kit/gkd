package li.gkd.app.domain.rule

import li.gkd.app.text.UiStrings
import li.gkd.app.data.ExcludeData
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot

enum class RuleSetting(val value: Boolean?, val label: String) {
    FollowDefault(null, UiStrings.setting_follow_default),
    Enabled(true, UiStrings.setting_manual_enabled),
    Disabled(false, UiStrings.setting_manual_disabled);

    companion object {
        fun from(value: Boolean?): RuleSetting = when (value) {
            null -> FollowDefault
            true -> Enabled
            false -> Disabled
        }
    }
}

// Switch scopes are explicit; a global group's switch and its per-app override
// are independent fields, even though they share a database row.
sealed interface RuleSwitchTarget {
    val subsId: Long
    data class App(override val subsId: Long, val appId: String) : RuleSwitchTarget
    data class AppGroup(override val subsId: Long, val appId: String, val groupKey: Int) : RuleSwitchTarget
    data class GlobalGroup(override val subsId: Long, val groupKey: Int) : RuleSwitchTarget
    data class GlobalApp(override val subsId: Long, val groupKey: Int, val appId: String) : RuleSwitchTarget
}

fun RuleGroupTarget.toSwitchTarget(): RuleSwitchTarget = when (this) {
    is RuleGroupTarget.App -> RuleSwitchTarget.AppGroup(subsId, appId, groupKey)
    is RuleGroupTarget.Global -> pageAppId?.let { RuleSwitchTarget.GlobalApp(subsId, groupKey, it) }
        ?: RuleSwitchTarget.GlobalGroup(subsId, groupKey)
}

class RuleConfigIndex(configs: SubscriptionConfigSnapshot) {
    private val subscriptions = configs.subsItems.associateBy { it.id }
    private val apps = configs.appConfigs.associateBy { it.subsId to it.appId }
    private val appGroups = configs.appGroupConfigs.associateBy { Triple(it.subsId, it.appId, it.groupKey) }
    private val globalGroups = configs.globalGroupConfigs.associateBy { it.subsId to it.groupKey }
    private val categories = configs.categoryConfigs.associateBy { it.subsId to it.categoryKey }
    private val globalAppOverrides = mutableMapOf<Pair<Long, Int>, Map<String, Boolean>>()

    fun hasSubscription(subsId: Long): Boolean = subsId in subscriptions
    fun subscriptionEnabled(subsId: Long): Boolean? = subscriptions[subsId]?.enable
    fun appEnabled(subsId: Long, appId: String): Boolean? = apps[subsId to appId]?.enable
    fun categoryConfig(subsId: Long, categoryKey: Int?): SubsCategoryConfig? =
        categoryKey?.let { categories[subsId to it] }

    fun groupConfig(target: RuleGroupTarget): SubsGroupConfig? = when (target) {
        is RuleGroupTarget.App -> appGroups[Triple(target.subsId, target.appId, target.groupKey)]
        is RuleGroupTarget.Global -> globalGroups[target.subsId to target.groupKey]
    }

    fun setting(target: RuleSwitchTarget): RuleSetting = RuleSetting.from(when (target) {
        is RuleSwitchTarget.App -> appEnabled(target.subsId, target.appId)
        is RuleSwitchTarget.AppGroup -> appGroups[Triple(target.subsId, target.appId, target.groupKey)]?.enable
        is RuleSwitchTarget.GlobalGroup -> globalGroups[target.subsId to target.groupKey]?.enable
        is RuleSwitchTarget.GlobalApp -> {
            val key = target.subsId to target.groupKey
            globalAppOverrides.getOrPut(key) { ExcludeData.parse(globalGroups[key]?.exclude).appIds }[target.appId]?.not()
        }
    })
}

data class RuleControlState(
    val setting: RuleSetting,
    val defaultEnabled: Boolean,
    val defaultSource: String,
    val scope: String,
    val restrictions: List<String> = emptyList(),
    val canEnable: Boolean = true,
    val limitations: RuleLimitations = RuleLimitations(),
    val blockedApp: Boolean = false,
) {
    val configuredEnabled: Boolean get() = setting.value ?: defaultEnabled
    val available: Boolean get() = configuredEnabled && canEnable && !blockedApp && restrictions.isEmpty()
    val hasCustomSetting: Boolean get() = setting != RuleSetting.FollowDefault
    val label: String get() = if (setting == RuleSetting.FollowDefault) {
        UiStrings.setting_follow_default_value(if (defaultEnabled) UiStrings.action_turn_on else UiStrings.action_close)
    } else setting.label
}
