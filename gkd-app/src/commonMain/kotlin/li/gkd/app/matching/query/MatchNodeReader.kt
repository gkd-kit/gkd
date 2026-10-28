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

class MatchNodeReadUnavailable(reason: String, cause: Throwable? = null) : CancellationException(reason) {
    init { if (cause != null) initCause(cause) }
}

/** Only platform reads outlive cancellation, never selector evaluation or cache updates. */
class MatchNodeReader(private val diagnostics: MatchDiagnostics? = null) : AutoCloseable {
    /** Called within query. Future.get is interruptible even when the platform read is not. */
    fun <T> read(timeoutMillis: Long = 500, name: String = "NodeRead", runId: Long = 0,
        rule: String? = null, page: Any? = null,
        arguments: List<MatchRecordedValue> = emptyList(), block: () -> T): T {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val trace = diagnostics?.takeIf { it.enabled }
        val observation = if (trace == null) null else MatchNodeObservation.current()
        val wait = trace?.beginOperation("${name}Wait", runId, rule, page,
            parentOperationId = observation?.operation?.id, arguments = arguments, sessionId = observation?.sessionId, recording = observation?.recording)
        var outcome = "returned"
        try {
            val future = if (trace == null || wait == null) FutureTask(block) else FutureTask {
                val operation = trace.beginOperation(if (name == "NodeRead") name else "NodeRead:$name", runId, rule, page,
                    parentOperationId = wait.id, arguments = arguments, sessionId = wait.sessionId, recording = observation?.recording)
                try {
                    block().also { trace.endOperation(operation, if (it == null) "null" else if (it is Boolean && !it) "false" else "returned") }
                } catch (error: Throwable) {
                    trace.endOperation(operation, error.toString())
                    throw error
                }
            }
            try {
                executor.execute(future)
            } catch (error: RejectedExecutionException) {
                val reason = if (executor.isShutdown) "node_reader_closed" else "node_read_capacity"
                if (trace?.enabled == true) trace.nodeReadPressure(reason, executor.activeCount, executor.maximumPoolSize,
                    sessionId = wait?.sessionId ?: observation?.sessionId)
                throw MatchNodeReadUnavailable(reason, error)
            }
            return try {
                future.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (error: TimeoutException) {
                if (trace?.enabled == true) trace.nodeReadPressure("node_read_timeout", executor.activeCount, executor.maximumPoolSize,
                    sessionId = wait?.sessionId ?: observation?.sessionId)
                throw MatchNodeReadUnavailable("node_read_timeout", error)
            } catch (error: ExecutionException) {
                throw checkNotNull(error.cause)
            } finally {
                // Do not interrupt Binder. A running task retains its pool slot until it really exits.
                future.cancel(false)
            }
        } catch (error: Throwable) {
            outcome = error.toString()
            throw error
        } finally {
            trace?.endOperation(wait, outcome)
        }
    }
}
