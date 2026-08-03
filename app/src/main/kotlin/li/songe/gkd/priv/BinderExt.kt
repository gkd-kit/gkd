package li.songe.gkd.priv

import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import priv.kit.core.binder.PrivilegeBinderWrapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask

fun IBinder.dump(vararg args: String): String {
    val outputPipe = ParcelFileDescriptor.createPipe()
    val output = outputPipe[0].readTextAsync("IBinder.dump")
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

fun IBinder.shellCommand(vararg args: String): ShellCommandResult {
    val stdoutPipe = ParcelFileDescriptor.createPipe()
    val stderrPipe = ParcelFileDescriptor.createPipe()
    val stdout = stdoutPipe[0].readTextAsync()
    val stderr = stderrPipe[0].readTextAsync()
    val resultLatch = CountDownLatch(1)
    var shellResultCode = -1
    var thrown: Throwable? = null

    try {
        val resultReceiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                shellResultCode = resultCode
                resultLatch.countDown()
            }
        }
        transactShellCommand(
            out = stdoutPipe[1],
            err = stderrPipe[1],
            args = arrayOf(*args),
            resultReceiver = resultReceiver,
        )
        resultLatch.await()
    } catch (e: Throwable) {
        e.printStackTrace()
        thrown = e
        resultLatch.countDown()
    } finally {
        stdoutPipe[1].closeQuietly()
        stderrPipe[1].closeQuietly()
    }

    val result = stdout.getTextOrEmpty()
    val error = stderr.getTextOrEmpty().ifBlank { thrown?.message }
    return ShellCommandResult(
        code = shellResultCode,
        result = result,
        error = error,
    )
}

private fun IBinder.transactShellCommand(
    out: ParcelFileDescriptor,
    err: ParcelFileDescriptor,
    args: Array<String>,
    resultReceiver: ResultReceiver,
) {
    val stdinPipe = ParcelFileDescriptor.createPipe()
    try {
        stdinPipe[1].closeQuietly()
        check(this is PrivilegeBinderWrapper) {
            "shellCommand requires PrivilegeBinderWrapper"
        }
        shellCommand(
            input = stdinPipe[0].fileDescriptor,
            output = out.fileDescriptor,
            error = err.fileDescriptor,
            args = args,
            shellCallback = null,
            resultReceiver = resultReceiver,
        )
    } finally {
        stdinPipe[0].closeQuietly()
        stdinPipe[1].closeQuietly()
    }
}

private fun ParcelFileDescriptor.readTextAsync(
    threadName: String = "IBinder.shellCommand",
): FutureTask<String> {
    val task = FutureTask {
        ParcelFileDescriptor.AutoCloseInputStream(this).bufferedReader().use { reader ->
            reader.readText()
        }
    }
    Thread(task, threadName).apply {
        isDaemon = true
        start()
    }
    return task
}

private fun FutureTask<String>.getTextOrEmpty(): String {
    return runCatching { get() }.getOrDefault("")
}

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}
