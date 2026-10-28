package li.gkd.app.util

import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.util.concurrent.FutureTask

fun IBinder.dump(vararg args: String): String {
    val outputPipe = ParcelFileDescriptor.createPipe()
    val output = outputPipe[0].readTextAsync()
    try {
        dump(outputPipe[1].fileDescriptor, args)
    } catch (e: Throwable) {
        outputPipe[0].closeQuietly()
        throw e
    } finally {
        outputPipe[1].closeQuietly()
    }
    val text = output.get()
    if (text.trimStart().startsWith("Permission Denial:")) {
        throw SecurityException(text.trim())
    }
    check(text.isNotBlank()) { "System service dump is empty" }
    return text
}

private fun ParcelFileDescriptor.readTextAsync(): FutureTask<String> {
    val task = FutureTask {
        ParcelFileDescriptor.AutoCloseInputStream(this).bufferedReader().use { reader ->
            reader.readText()
        }
    }
    Thread(task, "IBinder.dump").apply {
        isDaemon = true
        start()
    }
    return task
}

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}
