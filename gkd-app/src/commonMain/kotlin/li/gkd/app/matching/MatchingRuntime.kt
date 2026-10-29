package li.gkd.app.matching

import kotlinx.coroutines.Dispatchers
import li.gkd.app.app.applicationScope
import li.gkd.app.logging.LogUtils
import li.gkd.app.matching.diagnostics.MatchDiagnosticFiles
import li.gkd.app.matching.diagnostics.matchForegroundSnapshot
import li.gkd.app.matching.input.ForegroundIdentity
import li.gkd.app.matching.input.MatchInputStream
import li.gkd.app.matching.query.MatchNodeReader
import li.gkd.app.rule.TopActivity

object MatchingRuntime {
    val nodeReader by lazy {
        MatchNodeReader(coordinator.diagnostics).also { reader ->
            checkNotNull(applicationScope().coroutineContext[kotlinx.coroutines.Job])
                .invokeOnCompletion { reader.close() }
        }
    }
    val inputs by lazy {
        MatchInputStream(
            ForegroundIdentity(TopActivity().appId),
            capture = { coordinator.diagnostics.enabled },
            record = { before, input, decision ->
                coordinator.diagnostics.recordInput(before, input, decision)
            },
        )
    }
    val coordinator by lazy {
        MatchCoordinator(
            applicationScope(),
            Dispatchers.Default,
            foregroundSnapshot = { matchForegroundSnapshot() },
            onDiagnosticCapture = MatchDiagnosticFiles::enqueue,
            onFailure = { LogUtils.e(it) },
        )
    }
}
