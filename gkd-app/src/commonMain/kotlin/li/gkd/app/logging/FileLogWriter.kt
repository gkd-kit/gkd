package li.gkd.app.logging

import li.gkd.app.time.format
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.days

data class LogMetadata(
    val systemName: String,
    val systemVersion: String,
    val device: String,
    val app: String
)

/** One application-owned writer; arguments have already been rendered on the calling thread. */
class FileLogWriter(private val directory: File, private val metadata: LogMetadata) :
    AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gkd-log-writer").apply { isDaemon = true }
    }
    private val failure = AtomicReference<Exception?>()

    fun append(tag: String, thread: String, location: String, texts: List<String>, time: Long) {
        executor.execute {
            try {
                write(tag, thread, location, texts, time)
            } catch (e: Exception) {
                failure.compareAndSet(null, e)
                // Never send a log-write failure back into the same writer.
                writePlatformLog("GKD", "Log file write failed: ${e.stackTraceToString()}")
            }
        }
    }

    /** Waits for entries submitted before this call. A failed write is reported once, never replayed. */
    fun flush() {
        executor.submit {}.get(5, TimeUnit.SECONDS)
        checkFailure()
    }

    override fun close() {
        executor.shutdown()
        if (!executor.awaitTermination(
                5,
                TimeUnit.SECONDS
            )
        ) throw IOException("Timed out draining log writer")
        checkFailure()
    }

    private fun checkFailure() {
        failure.getAndSet(null)?.let { throw IOException("Log file write failed", it) }
    }

    private fun write(
        tag: String,
        thread: String,
        location: String,
        texts: List<String>,
        time: Long
    ) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create log directory: $directory" }
        val file = directory.resolve("gkd-${time.format("yyyyMMdd")}.log")
        val text = buildString {
            if (!file.exists()) {
                val files = directory.listFiles()
                if (files != null && files.size >= 7) {
                    files.forEach { if (time - it.lastModified() > 7.days.inWholeMilliseconds) it.delete() }
                }
                append("=== Log ===\n")
                append("Date: ${time.format("yyyy-MM-dd HH:mm:ss.SSS")}\n")
                append("${metadata.systemName}: ${metadata.systemVersion}\n")
                append("Device: ${metadata.device}\n")
                append("App: ${metadata.app}\n")
                append("=== Log ===\n\n")
            }
            append("${time.format("HH:mm:ss.SSS")} $tag, $thread, $location")
            texts.forEachIndexed { index, value ->
                append(if (texts.size == 1) "\n" else "\n[$index]: ")
                append(value)
            }
            append("\n\n")
        }
        file.appendText(text)
    }
}
