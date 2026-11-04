package li.gkd.app.matching.query

import kotlinx.coroutines.CancellationException
import li.gkd.app.matching.diagnostics.MatchDiagnostics

/** Cross-user access and a disconnected UiAutomation can make windows temporarily unavailable. */
inline fun <T> readA11yWindow(
    trace: MatchDiagnostics,
    operation: String,
    noinline onUnavailable: ((RuntimeException?) -> Unit)? = null,
    read: () -> T,
): T? {
    val result =
        try {
            trace.observeOperation(operation, block = read)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            onUnavailable?.invoke(error)
            return null
        } catch (error: IllegalStateException) {
            onUnavailable?.invoke(error)
            return null
        }
    if (result == null) onUnavailable?.invoke(null)
    return result
}
