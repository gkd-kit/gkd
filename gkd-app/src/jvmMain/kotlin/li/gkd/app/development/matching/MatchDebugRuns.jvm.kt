package li.gkd.app.development.matching

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchDebugResult
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest

private object SimulatedMatchingService

actual suspend fun runMatchDebug(
    request: MatchDebugRunRequest,
    onStarted: (Long) -> Unit,
): MatchDebugResult {
    val simulation =
        requireNotNull(request.simulation) { "Desktop requires an explicit simulated node tree" }
    require(
        simulation.rootDelayMillis in 0..30000 &&
            simulation.actionMillis in 0..60000 &&
            simulation.cleanupMillis in 0..30000,
    )
    val coordinator = MatchingRuntime.coordinator
    val rule =
        prepareDebugRule(
            request,
            mapOf(simulation.app.id to simulation.app),
            recording = { coordinator.diagnostics.systemRecorder?.takeUnless { it.sealed } },
        )
    coordinator.attach(SimulatedMatchingService)
    return coordinator.execute(SimulatedMatchingService) {
        onStarted(id)
        val adapter =
            SimulatedNodeAdapter(simulation.root, checkNotNull(currentCoroutineContext()[Job]))
        val result =
            evaluateDebugRule(
                this,
                request,
                rule,
                simulation.app.id,
                simulation.activityId,
                SimulatedRuleNodes(simulation, adapter, this, coordinator.diagnostics),
            )
        result.copy(simulation = true)
    }
}
