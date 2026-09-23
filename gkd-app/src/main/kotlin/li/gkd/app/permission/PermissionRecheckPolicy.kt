package li.gkd.app.permission

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest

/** Read-only retries for system permission state that may lag behind returning from Settings. */
class PermissionRecheckPolicy(private val delaysMillis: List<Long> = emptyList()) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun awaitGranted(
        hostInteractive: StateFlow<Boolean>,
        refresh: () -> Boolean,
    ): Boolean = hostInteractive.transformLatest { interactive ->
        if (interactive) {
            var granted = refresh()
            for (delayMillis in delaysMillis) {
                if (granted) break
                delay(delayMillis)
                granted = refresh()
            }
            emit(granted)
        }
    }.first()

    companion object {
        val Immediate = PermissionRecheckPolicy()
        val Settings = PermissionRecheckPolicy(listOf(250L, 750L, 1_500L))
    }
}
