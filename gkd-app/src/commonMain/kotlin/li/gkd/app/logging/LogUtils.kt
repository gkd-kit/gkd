package li.gkd.app.logging

import li.gkd.app.META
import li.gkd.app.storage.appStorage
import li.songe.codeorigin.CallSite

object LogUtils {
    private val lock = Any()
    private val writer = lazy { FileLogWriter(appStorage().log, logMetadata()) }
    private var closed = false
    val debugEnabled: Boolean
        get() = META.debuggable

    fun d(
        vararg args: Any?,
        @CallSite loc: String = "",
        @CallSite("{file}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
    ) {
        if (!debugEnabled) return
        write(LogLevel.DEBUG, args, loc, tag)
    }

    fun i(
        vararg args: Any?,
        @CallSite loc: String = "",
        @CallSite("{file}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
    ) {
        write(LogLevel.INFO, args, loc, tag)
    }

    fun w(
        vararg args: Any?,
        @CallSite loc: String = "",
        @CallSite("{file}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
    ) {
        write(LogLevel.WARN, args, loc, tag)
    }

    fun e(
        vararg args: Any?,
        @CallSite loc: String = "",
        @CallSite("{file}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
    ) {
        write(LogLevel.ERROR, args, loc, tag)
    }

    /**
     * Build expensive details only when Debug logging is enabled; snapshot on the caller thread.
     */
    fun d(
        @CallSite loc: String = "",
        @CallSite("{file}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
        message: () -> Any?,
    ) {
        if (!debugEnabled) return
        write(LogLevel.DEBUG, arrayOf(message()), loc, tag)
    }

    private fun write(level: LogLevel, args: Array<out Any?>, loc: String, tag: String) {
        val thread = Thread.currentThread().name
        val location = loc.removePrefix("li.gkd.app.")
        val texts = args.map(::formatLogValue)
        synchronized(lock) {
            if (closed || META.debuggable || level >= LogLevel.WARN) {
                writePlatformLog(
                    level,
                    tag,
                    buildString {
                        append("$thread, $location")
                        texts.forEachIndexed { index, text ->
                            append(if (texts.size == 1) "\n" else "\n[$index]: ")
                            append(text)
                        }
                    },
                )
            }
            if (!closed)
                writer.value.append(tag, thread, location, texts, System.currentTimeMillis(), level)
        }
    }

    fun status() =
        synchronized(lock) { if (writer.isInitialized()) writer.value.status() else null }

    fun flush() {
        val current =
            synchronized(lock) { if (!closed && writer.isInitialized()) writer.value else null }
        current?.flush()
    }

    fun copyLogs(target: java.io.File) {
        val current =
            synchronized(lock) {
                check(!closed) { "Log writer is closed" }
                writer.value
            }
        current.copyTo(target)
    }

    fun close() {
        val current =
            synchronized(lock) {
                if (closed) return
                closed = true
                if (writer.isInitialized()) writer.value else null
            }
        current?.close()
    }
}
