package li.songe.router

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class Router(
    private val pushHistory: suspend (Route) -> Unit,
    private val backHistory: suspend (result: Any?) -> Unit,
    private val scope: CoroutineScope,
) {

    fun navigate(page: Page, data: Any? = null) = scope.launch {
        navigateForResult<Any?>(page, data)
    }

    suspend fun <T> navigateForResult(page: Page, data: Any? = null): T {
        return suspendCoroutine { continuation ->
            scope.launch {
                pushHistory(Route(
                    page,
                    data,
                ) { result ->
                    continuation.resume(result as T)
                })
            }
        }
    }

    fun back(result: Any? = null) = scope.launch {
        backHistory(result)
    }
}