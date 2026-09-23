package li.gkd.app.permission

import li.gkd.app.text.UiStrings
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PermissionPrompt(
    val title: String,
    val message: String,
    val displayDelayMillis: Long = 500L,
)

data class PermissionDialogState(
    val id: Long,
    val title: String,
    val message: String,
    val confirmText: String,
    val dismissText: String = UiStrings.action_cancel,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

class PermissionRequestCoordinator {
    private val lock = Any()
    private var activityResumed = false
    private var activityHasWindowFocus = false
    private var nextId = 0L
    private var activePrompt: ActivePrompt? = null

    val visiblePrompt: StateFlow<PermissionPrompt?>
        field = MutableStateFlow(null)
    val dialog: StateFlow<PermissionDialogState?>
        field = MutableStateFlow(null)

    fun updateHostState(
        resumed: Boolean,
        hasWindowFocus: Boolean,
    ) {
        synchronized(lock) {
            activityResumed = resumed
            activityHasWindowFocus = hasWindowFocus
            if (!resumed || !hasWindowFocus) {
                revealPromptLocked()
            }
            dismissReturnedPromptLocked()
        }
    }

    fun dispose() {
        val dialogToDismiss = synchronized(lock) {
            activityResumed = false
            activityHasWindowFocus = false
            activePrompt = null
            visiblePrompt.value = null
            dialog.value.also {
                dialog.value = null
            }
        }
        dialogToDismiss?.onDismiss?.invoke()
    }

    fun beginPrompt(prompt: PermissionPrompt): AutoCloseable {
        val id = synchronized(lock) {
            val newId = ++nextId
            activePrompt = ActivePrompt(
                id = newId,
                prompt = prompt,
            )
            visiblePrompt.value = null
            if (!activityResumed || !activityHasWindowFocus) {
                revealPromptLocked()
            }
            newId
        }
        return PromptSession(id)
    }

    suspend fun awaitResolution(permissionState: PermissionState): Boolean {
        val resolution = permissionState.resolution ?: return false
        return suspendCancellableCoroutine { continuation ->
            val id = showDialog(
                title = UiStrings.permission_request_title,
                message = listOfNotNull(
                    permissionState.purpose,
                    resolution.message,
                ).joinToString("\n\n"),
                confirmText = resolution.confirmText,
                onConfirm = {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                },
                onDismiss = {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                },
            )
            continuation.invokeOnCancellation {
                dismissDialog(id)
            }
        }
    }

    fun confirmDialog(id: Long) {
        takeDialog(id)?.onConfirm?.invoke()
    }

    fun dismissDialog(id: Long) {
        takeDialog(id)?.onDismiss?.invoke()
    }

    private fun showDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit,
    ): Long {
        val previousDialog: PermissionDialogState?
        val id: Long
        synchronized(lock) {
            previousDialog = dialog.value
            id = ++nextId
            dialog.value = PermissionDialogState(
                id = id,
                title = title,
                message = message,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
        previousDialog?.onDismiss?.invoke()
        return id
    }

    private fun takeDialog(id: Long): PermissionDialogState? = synchronized(lock) {
        dialog.value?.takeIf { it.id == id }?.also {
            dialog.value = null
        }
    }

    private fun revealPromptLocked() {
        val prompt = activePrompt ?: return
        if (prompt.visible) return
        prompt.visible = true
        visiblePrompt.value = prompt.prompt
    }

    private fun dismissReturnedPromptLocked() {
        val prompt = activePrompt ?: return
        if (
            prompt.visible &&
            activityResumed &&
            (activityHasWindowFocus || prompt.completed)
        ) {
            clearPromptLocked()
        }
    }

    private fun finishPrompt(id: Long) {
        synchronized(lock) {
            val prompt = activePrompt ?: return
            if (prompt.id != id) return
            prompt.completed = true
            if (!prompt.visible) {
                clearPromptLocked()
            } else {
                dismissReturnedPromptLocked()
            }
        }
    }

    private fun clearPromptLocked() {
        activePrompt = null
        visiblePrompt.value = null
    }

    private inner class PromptSession(
        private val id: Long,
    ) : AutoCloseable {
        private val closed = atomic(false)

        override fun close() {
            if (closed.compareAndSet(expect = false, update = true)) {
                finishPrompt(id)
            }
        }
    }

    private data class ActivePrompt(
        val id: Long,
        val prompt: PermissionPrompt,
        var visible: Boolean = false,
        var completed: Boolean = false,
    )
}
