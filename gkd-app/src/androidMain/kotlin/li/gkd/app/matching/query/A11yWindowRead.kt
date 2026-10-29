package li.gkd.app.matching.query

import kotlinx.coroutines.CancellationException
import li.gkd.app.matching.diagnostics.MatchDiagnostics

/** Cross-user access and a disconnected UiAutomation can make windows temporarily unavailable. */
inline fun <T> readA11yWindow(trace: MatchDiagnostics, operation: String, read: () -> T): T? =
    try {
        trace.observeOperation(operation, block = read)
    } catch (error: CancellationException) {
        throw error
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
