package li.gkd.app.permission

import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PermissionHostCommands {
    sealed interface Command {
        val id: Long

        data class RequestPermission(
            override val id: Long,
            val permission: IPermission,
            val prompt: PermissionPrompt,
        ) : Command

        data class OpenPermissionSettings(
            override val id: Long,
            val permission: IPermission,
        ) : Command
    }

    private data class PendingCommand(
        val command: Command,
        val launched: Boolean = false,
        val leftHost: Boolean = false,
    )

    private var nextCommandId = 0L
    private var continuation: CancellableContinuation<Unit>? = null
    private val commandFlow = MutableStateFlow<PendingCommand?>(null)

    suspend fun requestPermission(
        permission: IPermission,
        prompt: PermissionPrompt,
    ) {
        awaitCommand(
            Command.RequestPermission(
                id = nextCommandId(),
                permission = permission,
                prompt = prompt,
            ),
        )
    }

    suspend fun openPermissionSettings(permission: IPermission) {
        awaitCommand(
            Command.OpenPermissionSettings(
                id = nextCommandId(),
                permission = permission,
            ),
        )
    }

    suspend fun collectCommands(launch: (Command) -> Unit) {
        commandFlow.collect {
            takeCommandToLaunch()?.let(launch)
        }
    }

    fun complete(id: Long) {
        val currentContinuation = synchronized(this) {
            val pending = commandFlow.value
            if (pending?.command?.id != id) return
            commandFlow.value = null
            continuation.also { continuation = null }
        }
        if (currentContinuation?.isActive == true) {
            currentContinuation.resume(Unit)
        }
    }

    fun fail(id: Long, error: Throwable) {
        val currentContinuation = synchronized(this) {
            val pending = commandFlow.value
            if (pending?.command?.id != id) return
            commandFlow.value = null
            continuation.also { continuation = null }
        }
        if (currentContinuation?.isActive == true) {
            currentContinuation.resumeWithException(error)
        }
    }

    fun updateHostState(
        resumed: Boolean,
        hasWindowFocus: Boolean,
    ) {
        val returnedCommandId = synchronized(this) {
            val pending = commandFlow.value ?: return@synchronized null
            if (pending.launched && (!resumed || !hasWindowFocus)) {
                commandFlow.value = pending.copy(leftHost = true)
                null
            } else if (pending.launched && pending.leftHost) {
                pending.command.id
            } else {
                null
            }
        }
        returnedCommandId?.let(::complete)
    }

    fun detachHost() {
        synchronized(this) {
            val pending = commandFlow.value
            if (pending?.launched == true) {
                commandFlow.value = pending.copy(leftHost = true)
            }
        }
    }

    fun dispose() {
        val currentContinuation = synchronized(this) {
            commandFlow.value = null
            continuation.also { continuation = null }
        }
        currentContinuation?.cancel()
    }

    private fun nextCommandId(): Long = ++nextCommandId

    private suspend fun awaitCommand(command: Command) {
        suspendCancellableCoroutine { currentContinuation ->
            synchronized(this) {
                check(commandFlow.value == null) { "A permission platform request is already in progress" }
                continuation = currentContinuation
                commandFlow.value = PendingCommand(command)
            }
            currentContinuation.invokeOnCancellation {
                synchronized(this) {
                    if (continuation === currentContinuation) {
                        continuation = null
                        commandFlow.value = null
                    }
                }
            }
        }
    }

    private fun takeCommandToLaunch(): Command? = synchronized(this) {
        val pending = commandFlow.value ?: return@synchronized null
        if (pending.launched) return@synchronized null
        commandFlow.value = pending.copy(launched = true)
        pending.command
    }
}
