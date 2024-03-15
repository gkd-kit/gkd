package li.songe.gkd.shizuku

import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import kotlinx.serialization.encodeToString
import li.songe.gkd.util.json
import java.io.DataOutputStream


class UserService : IUserService.Stub {
    /**
     * Constructor is required.
     */
    constructor() {
        Log.i("UserService", "constructor")
    }

    @Keep
    constructor(context: Context) {
        Log.i("UserService", "constructor with Context: context=$context")
    }

    /**
     * Reserved destroy method
     */
    override fun destroy() {
        Log.i("UserService", "destroy")
    }

    override fun exit() {
        destroy()
    }

    @Throws(RemoteException::class)
    override fun execCommand(command: String): String {
        val process = Runtime.getRuntime().exec("sh")
        val outputStream = DataOutputStream(process.outputStream)
        val commandResult = try {
            command.split('\n').filter { it.isNotBlank() }.forEach {
                outputStream.write(it.toByteArray())
                outputStream.writeBytes('\n'.toString())
                outputStream.flush()
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            CommandResult(
                code = process.waitFor(),
                result = process.inputStream.bufferedReader().readText(),
                error = process.errorStream.bufferedReader().readText(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val message = e.message
            val aimErrStr = "error="
            val index = message?.indexOf(aimErrStr)
            val code = if (index != null) {
                message.substring(index + aimErrStr.length)
                    .takeWhile { c -> c.isDigit() }
                    .toIntOrNull()
            } else {
                null
            } ?: 1
            CommandResult(
                code = code,
                result = "",
                error = e.message,
            )
        } finally {
            outputStream.close()
            process.inputStream.close()
            process.outputStream.close()
            process.destroy()
        }
        return json.encodeToString(commandResult)
    }
}

