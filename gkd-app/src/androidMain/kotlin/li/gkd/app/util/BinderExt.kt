package li.gkd.app.util

import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.util.concurrent.FutureTask

fun IBinder.dump(vararg args: String): String {
    val text = dump(*args) { it.readText() }
    if (text.trimStart().startsWith("Permission Denial:")) {
        throw SecurityException(text.trim())
    }
    check(text.isNotBlank()) { "System service dump is empty" }
    return text
}

/** The reader is closed before returning; consume its contents inside [read]. */
fun <T> IBinder.dump(vararg args: String, read: (BufferedReader) -> T): T {
    val outputPipe = ParcelFileDescriptor.createPipe()
    val output = FutureTask {
        ParcelFileDescriptor.AutoCloseInputStream(outputPipe[0]).bufferedReader().use(read)
    }
    Thread(output, "IBinder.dump").apply {
        isDaemon = true
        start()
    }
    try {
        dump(outputPipe[1].fileDescriptor, args)
    } catch (e: Throwable) {
        outputPipe[0].closeQuietly()
        throw e
    } finally {
        outputPipe[1].closeQuietly()
    }
    return output.get()
}

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}
