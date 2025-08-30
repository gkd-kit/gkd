package li.songe.gkd.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.util.componentName
import li.songe.gkd.util.json
import rikka.shizuku.Shizuku
import java.io.DataOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.system.exitProcess


@Suppress("unused")
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

    override fun destroy() {
        Log.i("UserService", "destroy")
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

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

private fun unbindUserService(serviceArgs: Shizuku.UserServiceArgs, connection: ServiceConnection) {
    if (!shizukuOkState.stateFlow.value) return
    LogUtils.d("unbindUserService", serviceArgs)
    // https://github.com/RikkaApps/Shizuku-API/blob/master/server-shared/src/main/java/rikka/shizuku/server/UserServiceManager.java#L62
    try {
        Shizuku.unbindUserService(serviceArgs, connection, false)
        Shizuku.unbindUserService(serviceArgs, connection, true)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Serializable
data class CommandResult(
    val code: Int?,
    val result: String,
    val error: String?
) {
    val ok: Boolean
        get() = code == 0
}

data class UserServiceWrapper(
    val userService: IUserService,
    val connection: ServiceConnection,
    val serviceArgs: Shizuku.UserServiceArgs
) {
    fun destroy() = unbindUserService(serviceArgs, connection)

    fun execCommandForResult(command: String): CommandResult = try {
        val resultStr = userService.execCommand(command)
        val result = json.decodeFromString<CommandResult>(resultStr)
        result
    } catch (e: Throwable) {
        e.printStackTrace()
        CommandResult(code = null, result = "", error = e.message)
    }

    fun safeTap(x: Float, y: Float, duration: Long? = null): Boolean? {
        val command = if (duration != null) {
            "input swipe $x $y $x $y $duration"
        } else {
            "input tap $x $y"
        }
        return execCommandForResult(command).ok
    }
}

suspend fun buildServiceWrapper(): UserServiceWrapper? {
    val serviceArgs = Shizuku
        .UserServiceArgs(UserService::class.componentName)
        .daemon(false)
        .processNameSuffix("shizuku-user-service")
        .debuggable(META.debuggable)
        .version(META.versionCode)
        .tag("default")
    LogUtils.d("buildServiceWrapper", serviceArgs)
    var resumeCallback: ((UserServiceWrapper) -> Unit)? = null
    val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            LogUtils.d("onServiceConnected", componentName)
            resumeCallback ?: return
            if (binder?.pingBinder() == true) {
                resumeCallback?.invoke(
                    UserServiceWrapper(
                        IUserService.Stub.asInterface(binder),
                        this,
                        serviceArgs
                    )
                )
                resumeCallback = null
            } else {
                LogUtils.d("invalid binder for $componentName received")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            LogUtils.d("onServiceDisconnected", componentName)
        }
    }
    return withTimeoutOrNull(3000) {
        suspendCoroutine { continuation ->
            resumeCallback = { continuation.resume(it) }
            try {
                Shizuku.bindUserService(serviceArgs, connection)
            } catch (_: Throwable) {
                resumeCallback = null
                continuation.resume(null)
            }
        }
    }.apply {
        if (this == null) {
            unbindUserService(serviceArgs, connection)
        }
    }
}
