package li.gkd.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class SimulatorSaveStatus(val pending: Boolean = false, val error: String? = null)

/** Explicit commands carry complete snapshots; only this worker touches the configuration file. */
class SimulatorPersistence(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    file: File = DesktopStorage.sessionDirectory.resolve("simulator.json"),
    environment: DesktopEnvironment = DesktopEnvironment(),
) {
    private val storage = DesktopSimulatorStorage(file)
    val store = SimulatorStore(storage.load(environment), ::submit)

    private sealed interface Command {
        data class Snapshot(val revision: Long, val value: SimulatorSettings) : Command
        data class Flush(val result: CompletableDeferred<Boolean>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var revision = 0L
    val status: StateFlow<SimulatorSaveStatus>
        field = MutableStateFlow(SimulatorSaveStatus())

    @Synchronized
    private fun submit(value: SimulatorSettings) {
        commands.trySend(Command.Snapshot(++revision, value)).getOrThrow()
        status.value = SimulatorSaveStatus(pending = true)
    }

    @Synchronized
    private fun completed(snapshot: Command.Snapshot, error: Exception?) {
        if (snapshot.revision == revision) {
            status.value = SimulatorSaveStatus(
                error != null,
                error?.let { "${it.javaClass.simpleName}: ${it.message}" })
        }
    }

    private fun persist(snapshot: Command.Snapshot): Boolean {
        return try {
            storage.save(snapshot.value)
            completed(snapshot, null)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            completed(snapshot, e)
            false
        }
    }

    private val worker = scope.launch(dispatcher) {
        var pending: Command.Snapshot? = null
        var scheduled = false
        while (isActive) {
            val command =
                if (scheduled) withTimeoutOrNull(200) { commands.receive() } else commands.receive()
            when (command) {
                is Command.Snapshot -> {
                    pending = command; scheduled = true
                }

                is Command.Flush, null -> {
                    val success = pending?.let { persist(it) } ?: true
                    if (success) pending = null
                    scheduled = false
                    if (command is Command.Flush) command.result.complete(success)
                }
            }
        }
    }

    /** A barrier for all commands submitted before this call, also used for retry. */
    suspend fun flush(): Boolean {
        val result = CompletableDeferred<Boolean>()
        commands.send(Command.Flush(result))
        return result.await()
    }

    fun close() {
        worker.cancel(); commands.close()
    }
}
