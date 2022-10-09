package li.songe.gkd.shizuku

import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.InputStream


/**
 * https://github.com/Aefyr/SAI/blob/master/app/src/main/java/com/aefyr/sai/shell/ShizukuShell.java
 */
class ShizukuShell private constructor() : Shell {
    override val isAvailable: Boolean
        get() = if (!Shizuku.pingBinder()) false else try {
            exec(Shell.Command("echo", "test")).isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Unable to access shizuku: ")
            Log.w(TAG, e)
            false
        }

    override fun exec(command: Shell.Command): Shell.Result {
        return execInternal(command, null)
    }

    override fun exec(command: Shell.Command, inputPipe: InputStream): Shell.Result {
        return execInternal(command, inputPipe)
    }

    override fun makeLiteral(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    private fun execInternal(command: Shell.Command, inputPipe: InputStream?): Shell.Result {
        val stdOutSb = StringBuilder()
        val stdErrSb = StringBuilder()
        return try {
            val shCommand = Shell.Command.Builder("sh", "-c", command.toString())
            val process = Shizuku.newProcess(shCommand.build().toStringArray(), null, null)
            val stdOutD: Thread = IOUtils.writeStreamToStringBuilder(stdOutSb, process.inputStream)
            val stdErrD: Thread = IOUtils.writeStreamToStringBuilder(stdErrSb, process.errorStream)
            if (inputPipe != null) {
                try {
                    process.outputStream.use { outputStream ->
                        inputPipe.use { inputStream ->
                            IOUtils.copyStream(
                                inputStream,
                                outputStream
                            )
                        }
                    }
                } catch (e: Exception) {
                    stdOutD.interrupt()
                    stdErrD.interrupt()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        process.destroyForcibly()
                    } else {
                        process.destroy()
                    }
                    throw RuntimeException(e)
                }
            }
            process.waitFor()
            stdOutD.join()
            stdErrD.join()
            Shell.Result(
                command,
                process.exitValue(),
                stdOutSb.toString().trim { it <= ' ' },
                stdErrSb.toString().trim { it <= ' ' })
        } catch (e: Exception) {
            Log.w(TAG, "Unable execute command: ")
            Log.w(TAG, e)
            Shell.Result(
                command, -1, stdOutSb.toString().trim { it <= ' ' },
                """$stdErrSb

<!> SAI ShizukuShell Java exception: ${Utils.throwableToString(e)}"""
            )
        }
    }

    companion object {
        private const val TAG = "ShizukuShell"
        val instance by lazy { ShizukuShell() }
    }


}