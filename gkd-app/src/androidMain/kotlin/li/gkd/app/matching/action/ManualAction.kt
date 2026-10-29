package li.gkd.app.matching.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.inspection.InspectionException
import li.gkd.app.inspection.InspectionFailure
import li.gkd.app.matching.AndroidAutomationSessions
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.query.A11yQueryContext
import li.gkd.app.matching.query.selectorTypeModel
import li.gkd.app.model.ActionResult
import li.gkd.app.model.GkdAction
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.SelectorTypeResult

suspend fun executeManualAction(gkdAction: GkdAction): ActionResult {
    val selectorResult = Selector.compile(gkdAction.selector)
    val selector =
        (selectorResult as? SelectorCompileResult.Success)?.value
            ?: throw InspectionException(InspectionFailure.InvalidSelector)
    val typeResult = selector.validateType(selectorTypeModel)
    if (typeResult is SelectorTypeResult.Failure) {
        throw InspectionException(InspectionFailure.SelectorTypeError, typeResult.error.message)
    }
    val service =
        AndroidAutomationSessions.service
            ?: throw InspectionException(InspectionFailure.AccessibilityUnavailable)
    return MatchingRuntime.coordinator.execute(service) {
        ensureCurrent()
        val reader = MatchingRuntime.nodeReader
        val root =
            reader.query {
                reader.read(name = "Root", runId = id, rule = "manual") {
                    AndroidAutomationSessions.getRoot(service)
                }
            } ?: throw InspectionException(InspectionFailure.ScreenNodesUnavailable)
        val context =
            A11yQueryContext(
                getRoot = { root },
                interruptable = false,
                nodeReader = reader,
                diagnostics = MatchingRuntime.coordinator.diagnostics,
            )
        context.queryJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        context.queryRunId = id
        val targetNode =
            match(null) {
                reader.query {
                    context.querySelfOrSelector(
                        root,
                        selector,
                        MatchOptions(fastQuery = gkdAction.fastQuery),
                        diagnostics = MatchingRuntime.coordinator.diagnostics,
                    )
                }
            } ?: throw InspectionException(InspectionFailure.TargetNodeNotFound)
        withContext(Dispatchers.IO) {
            ensureCurrent()
            val result =
                performAction(gkdAction) {
                    ActionPerformer.getAction(gkdAction.action ?: ActionPerformer.None.action)
                        .perform(
                            targetNode,
                            gkdAction,
                            MatchingRuntime.coordinator.diagnostics.enabled,
                        )
                }
            trace(MatchStage.Action, result.outcomeReason, action = result)
            result
        }
    }
}
