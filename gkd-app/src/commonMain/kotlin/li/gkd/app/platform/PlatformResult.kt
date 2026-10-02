package li.gkd.app.platform

/**
 * Outcome of a platform capability. Success is defined by the function contract:
 * requesting installation succeeds when handed to the OS, not when installation finishes.
 * Operational failures throw their original exception; coroutine cancellation propagates.
 */
sealed interface PlatformResult<out T> {
    data class Success<T>(val value: T) : PlatformResult<T>
    data object Unsupported : PlatformResult<Nothing>
}
