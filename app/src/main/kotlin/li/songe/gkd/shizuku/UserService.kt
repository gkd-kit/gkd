package li.songe.gkd.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import android.view.SurfaceControlHidden
import androidx.annotation.Keep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.META
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.componentName
import rikka.shizuku.Shizuku
import java.io.DataOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.system.exitProcess


// https://github.com/RikkaApps/Shizuku/issues/1171#issuecomment-2952442340
@Keep
class UserService(val context: Context) : IUserService.Stub() {

    init {
        Log.d(
            "UserService",
            "constructor(context=${context.packageName},pid=${android.os.Process.myPid()},uid=${android.os.Process.myUid()})"
        )
    }

    override fun destroy() {
        Log.d("UserService", "destroy")
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun execCommand(command: String): CommandResult {
        Log.d("UserService", "execCommand(command=$command)")
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
        return commandResult
    }

    override fun takeScreenshot1(width: Int, height: Int): Bitmap? {
        return SurfaceControlHidden.screenshot(width, height)
    }

    override fun takeScreenshot2(
        crop: Rect,
        rotation: Int
    ): Bitmap? {
        val width = crop.width()
        val height = crop.height()
        return SurfaceControlHidden.screenshot(crop, width, height, rotation)
    }

    override fun takeScreenshot3(crop: Rect): Bitmap? {
        val width = crop.width()
        val height = crop.height()
        val displayToken = SurfaceControlHidden.getInternalDisplayToken()
        val captureArgs = SurfaceControlHidden.DisplayCaptureArgs.Builder(displayToken)
            .setSourceCrop(crop)
            .setSize(width, height)
            .build()
        val screenshotBuffer = SurfaceControlHidden.captureDisplay(captureArgs)
        return screenshotBuffer?.asBitmap()
    }

    override fun killLegacyService(): Int {
        val pid = android.os.Process.myPid()
        val idReg = "\\d+".toRegex()
        val legacyPids = execCommand("ps | grep '${context.packageName}:$shizukuPsSuffix'")
            .result.lineSequence()
            .mapNotNull { idReg.find(it)?.value?.toInt() }
            .filter { it != pid }.toList()
        if (legacyPids.isNotEmpty()) {
            execCommand(legacyPids.joinToString(";") { "kill $it" })
        }
        return legacyPids.size
    }
}

private const val shizukuPsSuffix = "shizuku-user-service"

private fun unbindUserService(
    serviceArgs: Shizuku.UserServiceArgs,
    connection: ServiceConnection,
    reason: String? = null,
) {
    if (!shizukuGrantedState.stateFlow.value) return
    LogUtils.d(serviceArgs, reason)
    // https://github.com/RikkaApps/Shizuku-API/blob/master/server-shared/src/main/java/rikka/shizuku/server/UserServiceManager.java#L62
    try {
        Shizuku.unbindUserService(serviceArgs, connection, false)
        Shizuku.unbindUserService(serviceArgs, connection, true)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

data class UserServiceWrapper(
    val userService: IUserService,
    val connection: ServiceConnection,
    val serviceArgs: Shizuku.UserServiceArgs
) {
    fun destroy() = unbindUserService(serviceArgs, connection)

    fun execCommandForResult(command: String): CommandResult = try {
        userService.execCommand(command)
    } catch (e: Throwable) {
        e.printStackTrace()
        CommandResult(code = null, result = "", error = e.message)
    }

    fun tap(x: Float, y: Float, duration: Long = 0): Boolean {
        val command = if (duration > 0) {
            "input swipe $x $y $x $y $duration"
        } else {
            "input tap $x $y"
        }
        return execCommandForResult(command).ok
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val command = "input swipe $x1 $y1 $x2 $y2 $duration"
        return execCommandForResult(command).ok
    }

    fun screencapFile(filePath: String): Boolean {
        val tempPath = "/data/local/tmp/screencap_${System.currentTimeMillis()}.png"
        val command = "screencap -p $tempPath"
        val r = execCommandForResult(command)
        if (r.ok) {
            File(tempPath).copyTo(File(filePath), overwrite = true)
            execCommandForResult("rm $tempPath")
        }
        return r.ok
    }
}

suspend fun buildServiceWrapper(): UserServiceWrapper? {
    val serviceArgs = Shizuku
        .UserServiceArgs(UserService::class.componentName)
        .daemon(false)
        .processNameSuffix(shizukuPsSuffix)
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
        suspendCancellableCoroutine { continuation ->
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
            unbindUserService(serviceArgs, connection, "connect timeout")
        }
    }
}
