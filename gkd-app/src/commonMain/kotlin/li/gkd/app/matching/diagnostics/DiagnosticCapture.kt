package li.gkd.app.matching.diagnostics

/** Starts a user-visible capture using the platform's lifetime owner. */
expect suspend fun startDiagnosticCapture(
    detailed: Boolean = true,
    ruleFilter: String? = null,
    recordQueries: Boolean = true,
    recordSystem: Boolean = true,
): MatchDiagnosticState
