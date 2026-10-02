package li.gkd.app.util

import li.gkd.app.logging.FileLogWriter
import li.gkd.app.logging.formatLogValue
import li.gkd.app.logging.isLogDebuggable
import li.gkd.app.logging.logMetadata
import li.gkd.app.logging.writePlatformLog
import li.gkd.app.storage.appStorage
import li.songe.codeorigin.CallSite

object LogUtils {
    private val writer = lazy { FileLogWriter(appStorage().log, logMetadata()) }
    private var closed = false

    /** After close, entries go only to the platform diagnostic output. */
    @Synchronized
    fun d(
        vararg args: Any?,
        @CallSite loc: String = "",
        @CallSite("{file}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
    ) {
        val thread = Thread.currentThread().name
        val location = loc.removePrefix("li.gkd.app.")
        val texts = args.map(::formatLogValue)
        if (closed || isLogDebuggable()) {
            writePlatformLog(tag, buildString {
                append("$thread, $location")
                texts.forEachIndexed { index, text ->
                    append(if (texts.size == 1) "\n" else "\n[$index]: ")
                    append(text)
                }
            })
        }
        if (!closed) writer.value.append(tag, thread, location, texts, System.currentTimeMillis())
    }

    @Synchronized
    fun flush() {
        if (!closed && writer.isInitialized()) writer.value.flush()
    }

    @Synchronized
    fun close() {
        if (!closed) {
            closed = true
            if (writer.isInitialized()) writer.value.close()
        }
    }
}
