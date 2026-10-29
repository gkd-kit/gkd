package li.gkd.app.matching

import li.gkd.app.app.AppInfoRepository
import li.gkd.app.matching.input.launcherAppId
import li.gkd.app.rule.RuleMatchEnvironment
import li.gkd.app.rule.RuleRuntime

object RuleExecutionHost {
    val runtime =
        RuleRuntime(
            environment = {
                RuleMatchEnvironment(
                    launcherAppId,
                    AppInfoRepository.snapshot?.systemApps.orEmpty(),
                )
            },
            cancelPending = { MatchingRuntime.coordinator.cancelRuleDelays(it) },
            observer = {
                MatchingRuntime.coordinator.diagnostics.systemRecorder?.takeUnless { it.sealed }
            },
        )
}
