package li.gkd.app.matching

import li.gkd.app.rule.RuleMatchEnvironment
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.matching.input.launcherAppId

object RuleExecutionHost {
    val runtime = RuleRuntime(
        observer = { MatchingRuntime.coordinator.diagnostics.systemRecorder?.takeUnless { it.sealed } },
    )
}
