package li.gkd.app.domain.rule

import li.gkd.app.data.ExcludeData
import li.gkd.db.SubsGroupConfig
import li.gkd.db.withEnable
import li.gkd.db.withExclude

object RuleSwitchPolicy {
    fun updateGroup(target: RuleSwitchTarget, current: SubsGroupConfig, setting: RuleSetting): SubsGroupConfig =
        when (target) {
            is RuleSwitchTarget.App -> error("App master switch does not belong to rule group configuration")
            is RuleSwitchTarget.AppGroup, is RuleSwitchTarget.GlobalGroup -> current.withEnable(setting.value)
            is RuleSwitchTarget.GlobalApp -> {
                val exclude = ExcludeData.parse(current.exclude)
                current.withExclude(exclude.copy(appIds = exclude.appIds.toMutableMap().apply {
                    val value = setting.value
                    if (value == null) remove(target.appId) else set(target.appId, !value)
                }).stringify())
            }
        }
}
