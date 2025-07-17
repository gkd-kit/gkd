package li.songe.gkd.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.annotation.Keep
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.gkd.META
import li.songe.gkd.appScope
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.store.shizukuStoreFlow
import li.songe.gkd.util.componentName
import li.songe.gkd.util.json
import li.songe.gkd.util.toast
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

private fun IUserService.execCommandForResult(command: String): Boolean? {
    return try {
        val result = execCommand(command)
        if (result != null) {
            json.decodeFromString<CommandResult>(result).code == 0
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
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
        LogUtils.d(e)
    }
}

data class UserServiceWrapper(
    val userService: IUserService,
    val connection: ServiceConnection,
    val serviceArgs: Shizuku.UserServiceArgs
) {
    fun destroy() {
        unbindUserService(serviceArgs, connection)
    }

    fun execCommandForResult(command: String): Boolean? {
        return userService.execCommandForResult(command)
    }
}

private val bindServiceMutex by lazy { Mutex() }
suspend fun buildServiceWrapper(): UserServiceWrapper? {
    if (bindServiceMutex.isLocked) {
        toast("正在获取 Shizuku 服务，请稍后再试")
        return null
    }
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
    bindServiceMutex.withLock {
        return withTimeoutOrNull(3000) {
            suspendCoroutine { continuation ->
                resumeCallback = { continuation.resume(it) }
                Shizuku.bindUserService(serviceArgs, connection)
            }
        }.apply {
            if (this == null) {
                toast("获取 Shizuku 服务超时失败")
                unbindUserService(serviceArgs, connection)
            }
        }
    }
}

private val shizukuServiceUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, shizukuStoreFlow) { shizukuOk, store ->
        shizukuOk && store.enableTapClick
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val serviceWrapperFlow by lazy {
    val stateFlow = MutableStateFlow<UserServiceWrapper?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuServiceUsedFlow.collect {
            if (it) {
                stateFlow.update { s -> s ?: buildServiceWrapper() }
            } else {
                stateFlow.update { s -> s?.destroy(); null }
            }
        }
    }
    stateFlow
}

suspend fun shizukuCheckUserService(): Boolean {
    return try {
        execCommandForResult("input tap 0 0")
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    }
}

suspend fun execCommandForResult(command: String): Boolean {
    return serviceWrapperFlow.updateAndGet {
        it ?: buildServiceWrapper()
    }?.execCommandForResult(command) == true
}

// 在 大麦 https://i.gkd.li/i/14605104 上测试产生如下 3 种情况
// 1. 点击不生效: 使用传统无障碍屏幕点击, 此种点击可被 大麦 通过 View.setAccessibilityDelegate 屏蔽
// 2. 点击概率生效: 使用 Shizuku 获取到的 InputManager.injectInputEvent 发出点击, 概率失效/生效, 原因未知
// 3. 点击生效: 使用 Shizuku 获取到的 shell input tap x y 发出点击 by safeTap, 暂未找到屏蔽方案
fun safeTap(x: Float, y: Float): Boolean? {
    return serviceWrapperFlow.value?.execCommandForResult("input tap $x $y")
}

fun safeLongTap(x: Float, y: Float, duration: Long): Boolean? {
    return serviceWrapperFlow.value?.execCommandForResult("input swipe $x $y $x $y $duration")
}
