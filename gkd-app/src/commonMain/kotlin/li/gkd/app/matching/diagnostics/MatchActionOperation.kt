package li.gkd.app.matching.diagnostics

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/** Carries the owning action across IO dispatch and non-cancellable platform cleanup. */
class MatchActionOperation(val diagnostics: MatchDiagnostics, val operation: MatchOperation) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<MatchActionOperation>
}

suspend inline fun <T> observeActionOperation(name: String, block: () -> T): T {
    val action = currentCoroutineContext()[MatchActionOperation] ?: return block()
    return action.diagnostics.observeOperation(
        name,
        action.operation.runId,
        action.operation.rule,
        parentOperationId = action.operation.id,
        sessionId = action.operation.sessionId,
        block = block,
    )
}
