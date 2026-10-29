package li.gkd.app.development.matching

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.action.ActionPerformer
import li.gkd.app.matching.diagnostics.MatchDebugResult
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest
import li.gkd.app.matching.input.AndroidForegroundState
import li.gkd.app.matching.query.A11yQueryContext
import li.gkd.app.matching.query.RuleQueryNodes
import li.gkd.app.rule.ResolvedRule

actual suspend fun runMatchDebug(
    request: MatchDebugRunRequest,
    onStarted: (Long) -> Unit,
): MatchDebugResult {
    require(request.simulation == null) { "Simulated trees require Desktop" }
    val service =
        checkNotNull(AndroidAutomationSessions.service) { "Accessibility service is unavailable" }
    check(AndroidAutomationSessions.isEffective(service)) {
        "Accessibility service has not connected"
    }
    check(!AndroidForegroundState.foregroundPending) { "Foreground is not confirmed" }
    val activity = AndroidForegroundState.currentRule.topActivity
    val apps = checkNotNull(AppInfoRepository.snapshot) { "Wait for the app catalog to load" }
    val rule =
        prepareDebugRule(
            request,
            apps.apps,
            recording = {
                MatchingRuntime.coordinator.diagnostics.systemRecorder?.takeUnless { it.sealed }
            },
        )
    return MatchingRuntime.coordinator.execute(service) {
        onStarted(id)
        val reader = MatchingRuntime.nodeReader
        val context =
            A11yQueryContext(
                getRoot = { service.windowNodeInfo },
                nodeReader = reader,
                diagnostics = MatchingRuntime.coordinator.diagnostics,
            )
        context.queryJob = currentCoroutineContext()[Job]
        context.queryRunId = id
        evaluateDebugRule(
            this,
            request,
            rule,
            activity.appId,
            activity.activityId,
            object : RuleQueryNodes<AccessibilityNodeInfo> {
                override suspend fun root() = reader.query {
                    reader.read(name = "Root", runId = id, rule = rule.matchIdentity()) {
                        service.windowNodeInfo
                    }
                }

                override fun appId(node: AccessibilityNodeInfo) = node.packageName?.toString()

                override suspend fun refresh(node: AccessibilityNodeInfo) = context.refresh(node)

                override suspend fun match(rule: ResolvedRule, node: AccessibilityNodeInfo) =
                    reader.query {
                        context.queryRule(rule, node, MatchingRuntime.coordinator.diagnostics)
                    }

                override suspend fun perform(rule: ResolvedRule, node: AccessibilityNodeInfo) =
                    ActionPerformer.getAction(
                            rule.parameters.action
                                ?: rule.parameters.position?.let {
                                    ActionPerformer.ClickCenter.action
                                }
                                ?: rule.parameters.swipeArg?.let { ActionPerformer.Swipe.action },
                        )
                        .perform(
                            node,
                            rule.parameters,
                            MatchingRuntime.coordinator.diagnostics.enabled,
                        )
            },
        )
    }
}
