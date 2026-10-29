package li.gkd.app.logging

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days

data class LogMetadata(
    val systemName: String,
    val systemVersion: String,
    val device: String,
    val app: String,
)

data class LogLimits(
    val queueEntries: Int = 1024,
    val queueBytes: Int = 2 * 1024 * 1024,
    val entryBytes: Int = 64 * 1024,
    val fileBytes: Long = 2L * 1024 * 1024,
    val totalBytes: Long = 16L * 1024 * 1024,
    val retentionMillis: Long = 7.days.inWholeMilliseconds,
) {
    init {
        require(queueEntries > 0 && entryBytes >= 256 && queueBytes >= entryBytes)
        require(fileBytes >= entryBytes * 2L && totalBytes >= fileBytes && retentionMillis > 0)
    }
}

data class LogWriterStatus(
    val queuedEntries: Int,
    val queuedBytes: Int,
    val dropped: Long,
    val truncated: Long,
    val failedBatches: Long,
    val lastFailure: String?,
)

/** Bounded queue of immutable caller snapshots. File I/O is owned by one worker. */
class FileLogWriter(
    private val directory: File,
    private val metadata: LogMetadata,
    private val limits: LogLimits = LogLimits(),
) : AutoCloseable {
    private data class Entry(
        val sequence: Long,
        val level: LogLevel,
        val time: Long,
        val bytes: ByteArray,
    ) {
        val size: Int
            get() = bytes.size + 13 // ASCII HH:mm:ss.SSS and one space, rendered by the worker.
    }

    // Object exposes wait()/notifyAll(), which are unavailable on Kotlin's Any.
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") private val lock = Object()
    private val filesLock = Any()
    private val queue = ArrayDeque<Entry>()
    private var firstInFlight: Long? = null
    private var sequence = 0L
    private var queuedBytes = 0
    private var dropped = 0L
    private var reportedDrops = 0L
    private var truncated = 0L
    private var failedBatches = 0L
    private var lastFailure: String? = null
    private var failure: Exception? = null
    private var failing = false
    private var closed = false

    fun append(
        tag: String,
        thread: String,
        location: String,
        texts: List<String>,
        time: Long,
        level: LogLevel = LogLevel.INFO,
    ) {
        val rendered = buildString {
            append("${level.name} $tag, $thread, $location")
            texts.forEachIndexed { index, text ->
                append(if (texts.size == 1) "\n" else "\n[$index]: ")
                append(text)
            }
            append("\n\n")
        }
            .toByteArray(Charsets.UTF_8)
        val oversized = rendered.size + 13 > limits.entryBytes
        val bytes = bound(rendered, limits.entryBytes - 13)
        val size = bytes.size + 13
        synchronized(lock) {
            check(!closed) { "Log writer is closed" }
            if (oversized) truncated++
            if (
                queuedBytes + size > limits.queueBytes &&
                    queuedBytes + size - queue.sumOf { if (it.level < level) it.size else 0 } >
                        limits.queueBytes
            ) {
                dropped++
                lock.notifyAll()
                return
            }
            while (queue.size >= limits.queueEntries || queuedBytes + size > limits.queueBytes) {
                val victim =
                    queue
                        .withIndex()
                        .filter { it.value.level < level }
                        .minByOrNull { it.value.level }
                if (victim == null) {
                    dropped++
                    lock.notifyAll()
                    return
                }
                val removed = queue.removeAt(victim.index)
                queuedBytes -= removed.size
                dropped++
            }
            val entry = Entry(++sequence, level, time, bytes)
            queue.addLast(entry)
            queuedBytes += size
            lock.notifyAll()
        }
    }

    private fun bound(bytes: ByteArray, budget: Int = limits.entryBytes): ByteArray {
        if (bytes.size <= budget) return bytes
        val suffix = "\n[log entry truncated]\n\n".toByteArray(Charsets.UTF_8)
        var end = budget - suffix.size
        // The first omitted byte must start a code point; never split an encoded character.
        while ((bytes[end].toInt() and 0xc0) == 0x80) end--
        return bytes.copyOf(end) + suffix
    }

    fun status(): LogWriterStatus =
        synchronized(lock) {
            LogWriterStatus(queue.size, queuedBytes, dropped, truncated, failedBatches, lastFailure)
        }

    /**
     * Wait for entries and loss summaries accepted before this call; later traffic cannot starve
     * it.
     */
    fun flush() {
        synchronized(lock) {
            val target = sequence
            val losses = dropped
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (
                firstInFlight?.let { it <= target } == true ||
                    queue.firstOrNull()?.let { it.sequence <= target } == true ||
                    reportedDrops < losses
            ) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw IOException("Timed out draining log writer")
                TimeUnit.NANOSECONDS.timedWait(lock, remaining)
            }
            checkFailure()
        }
    }

    /** Freeze the file set for sharing while producers continue using the bounded queue. */
    fun copyTo(target: File) {
        flush()
        synchronized(filesLock) {
            if (directory.exists()) directory.copyRecursively(target, overwrite = true)
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            lock.notifyAll()
        }
        worker.join(5000)
        if (worker.isAlive) throw IOException("Timed out draining log writer")
        synchronized(lock) { checkFailure() }
    }

    private fun checkFailure() {
        val error = failure
        failure = null
        if (error != null) throw IOException("Log file write failed", error)
    }

    private fun drain() {
        while (true) {
            val batch: List<Entry>
            val losses: Long
            val previousLosses: Long
            synchronized(lock) {
                while (queue.isEmpty() && dropped == reportedDrops && !closed) lock.wait()
                if (queue.isEmpty() && dropped == reportedDrops && closed) return
                previousLosses = reportedDrops
                losses = dropped
                batch = buildList {
                    repeat(minOf(queue.size, 64)) {
                        val entry = queue.removeFirst()
                        queuedBytes -= entry.size
                        add(entry)
                    }
                }
                firstInFlight = batch.firstOrNull()?.sequence
            }
            try {
                val entries =
                    if (losses == previousLosses) batch
                    else
                        listOf(
                            Entry(
                                0,
                                LogLevel.WARN,
                                batch.firstOrNull()?.time ?: System.currentTimeMillis(),
                                "WARN GKD: log_queue_dropped=${losses - previousLosses} total=$losses\n\n"
                                    .toByteArray(),
                            ),
                        ) + batch
                synchronized(filesLock) { writeBatch(entries) }
                synchronized(lock) { failing = false }
            } catch (error: Exception) {
                val report =
                    synchronized(lock) {
                        failedBatches++
                        lastFailure = error.toString()
                        if (failure == null) failure = error
                        (!failing).also { failing = true }
                    }
                // A persistent outage emits once; never feed it back into this writer.
                if (report)
                    writePlatformLog(
                        LogLevel.ERROR,
                        "GKD",
                        "Log file write failed: ${error.stackTraceToString()}",
                    )
            } finally {
                synchronized(lock) {
                    firstInFlight = null
                    reportedDrops = losses
                    lock.notifyAll()
                }
            }
        }
    }

    private val managedName = Regex("gkd-[0-9]{8}(?:-[0-9]+)?\\.log")

    private fun managedFiles() =
        directory.listFiles().orEmpty().filter { it.isFile && managedName.matches(it.name) }

    private var current: File? = null
    private var currentDate = ""
    private var part = 0
    private val worker =
        Thread(::drain, "gkd-log-writer").apply {
            isDaemon = true
            start()
        }

    private fun formatTime(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.ROOT).format(Date(time))

    private fun writeBatch(entries: List<Entry>) {
        check(directory.isDirectory || directory.mkdirs()) {
            "Cannot create log directory: $directory"
        }
        var output: OutputStream? = null
        var opened: File? = null
        var size = 0L
        try {
            for (entry in entries) {
                val date = formatTime(entry.time, "yyyyMMdd")
                val bytes =
                    (formatTime(entry.time, "HH:mm:ss.SSS") + " ").toByteArray(Charsets.UTF_8) +
                        entry.bytes
                if (date != currentDate || current?.exists() != true) {
                    currentDate = date
                    part =
                        managedFiles()
                            .filter { it.name.startsWith("gkd-$date") }
                            .maxOfOrNull {
                                it.nameWithoutExtension
                                    .removePrefix("gkd-$date")
                                    .removePrefix("-")
                                    .toIntOrNull() ?: 0
                            } ?: 0
                    current = directory.resolve("gkd-$date${if (part == 0) "" else "-$part"}.log")
                }
                var file = checkNotNull(current)
                val existingSize = if (opened == file) size else file.length()
                if (existingSize > 0 && existingSize + bytes.size > limits.fileBytes) {
                    part++
                    file = directory.resolve("gkd-$date-$part.log")
                    current = file
                }
                if (opened != file) {
                    output?.close()
                    size = file.length()
                    output = java.io.FileOutputStream(file, true).buffered()
                    opened = file
                    if (size == 0L) {
                        val header =
                            ("=== Log ===\nDate: ${formatTime(entry.time, "yyyy-MM-dd HH:mm:ss.SSS")}\n" +
                                    "${metadata.systemName}: ${metadata.systemVersion}\nDevice: ${metadata.device}\nApp: ${metadata.app}\n=== Log ===\n\n")
                                .toByteArray()
                        val boundedHeader = bound(header)
                        output.write(boundedHeader)
                        size += boundedHeader.size
                    }
                }
                checkNotNull(output).write(bytes)
                size += bytes.size
            }
        } finally {
            output?.close()
        }
        val now = entries.last().time
        val files = managedFiles().sortedByDescending { it.lastModified() }
        var total = files.sumOf { it.length() }
        // Delete oldest first, never unrelated files or the file just written.
        for (file in files.asReversed()) {
            if (file == current) continue
            if (now - file.lastModified() > limits.retentionMillis || total > limits.totalBytes) {
                val length = file.length()
                if (!file.delete()) throw IOException("Cannot remove old log: ${file.name}")
                total -= length
            }
        }
    }
}
