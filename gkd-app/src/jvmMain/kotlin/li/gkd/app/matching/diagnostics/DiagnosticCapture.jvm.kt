package li.gkd.app.matching.diagnostics

import li.gkd.app.matching.MatchingRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import li.gkd.app.resources.*
import li.gkd.app.ui.share.launchUi

actual suspend fun startDiagnosticCapture(detailed: Boolean, ruleFilter: String?,
    recordQueries: Boolean, recordSystem: Boolean): MatchDiagnosticState {
    val coordinator = MatchingRuntime.coordinator
    val session = coordinator.startDiagnostics(detailed, ruleFilter, recordQueries, recordSystem)
    li.gkd.app.app.applicationScope().launchUi {
        coordinator.diagnostics.state.first { it?.id != session.id || !it.active }
        MatchDiagnosticFiles.state.first { it.containsKey(session.id) }
        MatchDiagnosticFiles.flush()
        li.gkd.app.util.ToastUtils.show(org.jetbrains.compose.resources.getString(Res.string.match_debug_saved))
    }
    return session
}
