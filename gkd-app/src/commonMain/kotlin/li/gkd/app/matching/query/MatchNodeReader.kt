package li.gkd.app.matching.query

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchNodeObservation
import li.gkd.app.matching.diagnostics.MatchRecordedValue
import li.gkd.app.matching.diagnostics.NODE_READ_OPERATION_NAME

private const val NODE_READ_CAPACITY = 4
private const val DEFAULT_NODE_READ_TIMEOUT_MILLIS = 500L

enum class MatchNodeReadFailure(val wireValue: String) {
    ReaderClosed("node_reader_closed"),
    CapacityExceeded("node_read_capacity"),
    Timeout("node_read_timeout"),
}

class MatchNodeReadUnavailable(val reason: MatchNodeReadFailure, cause: Throwable? = null) :
    CancellationException(reason.wireValue) {
    init {
        if (cause != null) initCause(cause)
    }
}

/** Only platform reads outlive cancellation, never selector evaluation or cache updates. */
class MatchNodeReader(private val diagnostics: MatchDiagnostics? = null) : AutoCloseable {
    // Android retains an AccessibilityInteractionClient per thread ID. Keep these
    // threads for the reader's lifetime rather than leaving clients behind on idle expiry.
    private val executor =
        ThreadPoolExecutor(
            NODE_READ_CAPACITY,
            NODE_READ_CAPACITY,
            0,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            { task -> Thread(task, "match-node-read").apply { isDaemon = true } },
        )

    suspend fun <T> query(block: () -> T): T = runInterruptible(Dispatchers.IO) { block() }

    /** Called within query. Future.get is interruptible even when the platform read is not. */
    fun <T> read(
        timeoutMillis: Long = DEFAULT_NODE_READ_TIMEOUT_MILLIS,
        name: String = NODE_READ_OPERATION_NAME,
        runId: Long = 0,
        rule: String? = null,
        page: Any? = null,
        arguments: List<MatchRecordedValue> = emptyList(),
        block: () -> T,
    ): T {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val trace = diagnostics?.takeIf { it.enabled }
        val observation = if (trace == null) null else MatchNodeObservation.current()
        val wait =
            trace?.beginOperation(
                "${name}Wait",
                runId,
                rule,
                page,
                parentOperationId = observation?.operation?.id,
                arguments = arguments,
                sessionId = observation?.sessionId,
                recording = observation?.recording,
            )
        var outcome = "returned"
        try {
            val future =
                if (trace == null || wait == null) FutureTask(block)
                else
                    FutureTask {
                        trace.observeOperation(
                            if (name == NODE_READ_OPERATION_NAME) name
                            else "$NODE_READ_OPERATION_NAME:$name",
                            runId,
                            rule,
                            page,
                            parentOperationId = wait.id,
                            arguments = arguments,
                            sessionId = wait.sessionId,
                            recording = observation?.recording,
                            block = block,
                        )
                    }
            try {
                executor.execute(future)
            } catch (error: RejectedExecutionException) {
                val reason =
                    if (executor.isShutdown) MatchNodeReadFailure.ReaderClosed
                    else MatchNodeReadFailure.CapacityExceeded
                if (trace?.enabled == true)
                    trace.nodeReadPressure(
                        reason,
                        executor.activeCount,
                        executor.maximumPoolSize,
                        sessionId = wait?.sessionId ?: observation?.sessionId,
                    )
                throw MatchNodeReadUnavailable(reason, error)
            }
            return try {
                future.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (error: TimeoutException) {
                if (trace?.enabled == true)
                    trace.nodeReadPressure(
                        MatchNodeReadFailure.Timeout,
                        executor.activeCount,
                        executor.maximumPoolSize,
                        sessionId = wait?.sessionId ?: observation?.sessionId,
                    )
                throw MatchNodeReadUnavailable(MatchNodeReadFailure.Timeout, error)
            } catch (error: ExecutionException) {
                throw checkNotNull(error.cause)
            } finally {
                // Do not interrupt Binder. A running task retains its pool slot until it really
                // exits.
                future.cancel(false)
            }
        } catch (error: Throwable) {
            outcome = error.toString()
            throw error
        } finally {
            trace?.endOperation(wait, outcome)
        }
    }

    override fun close() {
        executor.shutdown()
    }
}
