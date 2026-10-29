package li.gkd.app.matching.diagnostics

import li.gkd.app.service.MatchDiagnosticService

actual suspend fun startDiagnosticCapture(
    detailed: Boolean,
    ruleFilter: String?,
    recordQueries: Boolean,
    recordSystem: Boolean,
): MatchDiagnosticState =
    MatchDiagnosticService.start(detailed, ruleFilter, recordQueries, recordSystem)
