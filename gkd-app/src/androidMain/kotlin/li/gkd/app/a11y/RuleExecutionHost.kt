package li.gkd.app.a11y

import li.gkd.app.app.AppInfoRepository
import li.gkd.app.rule.RuleDelayScheduler
import li.gkd.app.rule.RuleMatchEnvironment
import li.gkd.app.rule.RuleRuntime

object RuleExecutionHost {
    val delays = RuleDelayScheduler()
    val runtime = RuleRuntime(
        environment = {
            RuleMatchEnvironment(
                launcherAppId,
                AppInfoRepository.snapshot?.systemApps.orEmpty()
            )
        },
        cancelPending = delays::cancel,
    )
}
