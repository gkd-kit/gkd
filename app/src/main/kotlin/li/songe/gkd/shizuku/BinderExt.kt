package li.songe.gkd.shizuku

import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask

private const val SHELL_COMMAND_TRANSACTION = ('_'.code shl 24) or
    ('C'.code shl 16) or
    ('M'.code shl 8) or
    'D'.code

fun IBinder.shellCommand(vararg args: String): CommandResult {
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
    return CommandResult(
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
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    val stdinPipe = ParcelFileDescriptor.createPipe()
    try {
        stdinPipe[1].closeQuietly()
        data.writeFileDescriptor(stdinPipe[0].fileDescriptor)
        data.writeFileDescriptor(out.fileDescriptor)
        data.writeFileDescriptor(err.fileDescriptor)
        data.writeStringArray(args)
        data.writeStrongBinder(null)
        resultReceiver.writeToParcel(data, 0)
        transact(SHELL_COMMAND_TRANSACTION, data, reply, 0)
        reply.readException()
    } finally {
        stdinPipe[0].closeQuietly()
        stdinPipe[1].closeQuietly()
        data.recycle()
        reply.recycle()
    }
}

private fun ParcelFileDescriptor.readTextAsync(): FutureTask<String> {
    val task = FutureTask {
        ParcelFileDescriptor.AutoCloseInputStream(this).bufferedReader().use { reader ->
            reader.readText()
        }
    }
    Thread(task, "IBinder.shellCommand").apply {
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
