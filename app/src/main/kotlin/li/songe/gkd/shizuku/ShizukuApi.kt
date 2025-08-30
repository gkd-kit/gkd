package li.songe.gkd.shizuku


import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.IInterface
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.META
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.otherUserMapFlow
import li.songe.gkd.data.toAppInfo
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.MutexState
import li.songe.gkd.util.PKG_FLAGS
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.otherUserAppIconMapFlow
import li.songe.gkd.util.otherUserAppInfoMapFlow
import li.songe.gkd.util.pkgIcon
import li.songe.gkd.util.toast
import li.songe.gkd.util.userAppInfoMapFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmName

private val hiddenFunctionMap = HashMap<String, Int>()
fun IInterface.findCompatMethod(
    name: String,
    typePairs: List<Pair<Int, List<KType>>>
): Int {
    val key = "${this::class.jvmName}::$name"
    hiddenFunctionMap[key]?.let { return it }
    val functions = this::class.declaredMemberFunctions.filter { it.name == name }
    for (f in functions) {
        val types = f.valueParameters.map { it.type }
        typePairs.find { it.second == types }?.first?.let {
            hiddenFunctionMap[key] = it
            return it
        }
    }
    LogUtils.d(
        "获取签名 ${this::class.jvmName}::$name 失败",
        functions.joinToString("\n") {
            it.valueParameters.map { p -> p.type.toString() }.toString()
        }
    )
    hiddenFunctionMap[key] = -1
    return -1
}

// shizuku 会概率断开
inline fun <T> safeInvokeMethod(
    block: () -> T
): T? = try {
    block()
} catch (_: Throwable) {
    null
}

fun getStubService(name: String, condition: Boolean): ShizukuBinderWrapper? {
    if (!condition) return null
    val service = SystemServiceHelper.getSystemService(name) ?: return null
    return ShizukuBinderWrapper(service)
}

private val shizukuUsedFlow by lazy {
    combine(
        shizukuOkState.stateFlow,
        storeFlow.map { it.enableShizuku },
    ) { a, b ->
        a && b
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

class ShizukuContext(
    val serviceWrapper: UserServiceWrapper? = null,
    val packageManager: SafePackageManager? = null,
    val userManager: SafeUserManager? = null,
    val activityManager: SafeActivityManager? = null,
    val activityTaskManager: SafeActivityTaskManager? = null,
)

private val defaultShizukuContext = ShizukuContext()

val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

val shizukuContextFlow = MutableStateFlow(defaultShizukuContext)

fun safeGetTopCpn(): ComponentName? = shizukuContextFlow.value.run {
    activityTaskManager?.getTopCpn() ?: activityManager?.getTopCpn()
}

fun shizukuCheckGranted(): Boolean {
    val granted = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }
    if (!granted) return false
    val u = shizukuContextFlow.value.activityManager ?: SafeActivityManager.newBinder()
    return u?.getTopCpn() != null
}

val updateBinderMutex = MutexState()
private fun updateShizukuBinder() = appScope.launchTry(Dispatchers.IO) {
    updateBinderMutex.withStateLock {
        if (shizukuUsedFlow.value) {
            if (isActivityVisible()) {
                toast("正在连接 Shizuku 服务...")
            }
            shizukuContextFlow.value = ShizukuContext(
                serviceWrapper = buildServiceWrapper(),
                packageManager = SafePackageManager.newBinder(),
                userManager = SafeUserManager.newBinder(),
                activityManager = SafeActivityManager.newBinder(),
                activityTaskManager = SafeActivityTaskManager.newBinder()?.apply {
                    registerDefault()
                },
            )
            if (isActivityVisible()) {
                if (shizukuContextFlow.value.serviceWrapper == null) {
                    toast("Shizuku 服务连接失败")
                } else {
                    toast("Shizuku 服务连接成功")
                }
            }
        } else if (shizukuContextFlow.value != defaultShizukuContext) {
            shizukuContextFlow.value.run {
                serviceWrapper?.destroy()
                activityTaskManager?.unregisterDefault()
            }
            val prefix = if (isActivityVisible()) "" else "${META.appName}: "
            toast("${prefix}Shizuku 服务已断开")
        }
    }
}

fun updateOtherUserAppInfo() {
    val pkgManager = shizukuContextFlow.value.packageManager
    val userManager = shizukuContextFlow.value.userManager
    if (pkgManager == null || userManager == null) {
        otherUserMapFlow.value = emptyMap()
        otherUserAppIconMapFlow.value = emptyMap()
        otherUserAppInfoMapFlow.value = emptyMap()
        return
    }
    val otherUsers = userManager.getUsers().filter { it.id != currentUserId }.sortedBy { it.id }
    otherUserMapFlow.value = otherUsers.associateBy { it.id }
    val userPackageInfoMap = otherUsers.associate { user ->
        user.id to pkgManager.getInstalledPackages(
            PKG_FLAGS,
            user.id
        )
    }
    val newIconMap = HashMap<String, Drawable>()
    val userAppInfoMap = userAppInfoMapFlow.value
    val newAppMap = HashMap<String, AppInfo>()
    userPackageInfoMap.forEach { (userId, pkgInfoList) ->
        val diffPkgList = pkgInfoList.filter {
            !userAppInfoMap.contains(it.packageName) && !newAppMap.contains(
                it.packageName
            )
        }
        diffPkgList.forEach { pkgInfo ->
            newAppMap[pkgInfo.packageName] = pkgInfo.toAppInfo(
                userId = userId,
                hidden = pkgManager.checkAppHidden(pkgInfo.packageName),
            )
            pkgInfo.pkgIcon?.let { newIconMap[pkgInfo.packageName] = it }
        }
    }
    otherUserAppInfoMapFlow.value = newAppMap
    otherUserAppIconMapFlow.value = newIconMap
}

fun initShizuku() {
    Shizuku.addBinderReceivedListener {
        LogUtils.d("Shizuku.addBinderReceivedListener")
        appScope.launchTry(Dispatchers.IO) {
            shizukuOkState.updateAndGet()
        }
    }
    Shizuku.addBinderDeadListener {
        LogUtils.d("Shizuku.addBinderDeadListener")
        shizukuOkState.stateFlow.value = false
    }
    appScope.launchTry {
        shizukuUsedFlow.collect { updateShizukuBinder() }
    }
    appScope.launchTry(Dispatchers.IO) {
        combine(
            shizukuContextFlow,
            userAppInfoMapFlow,
        ) { a, b -> a to b }
            .debounce(3000)
            .collect { updateOtherUserAppInfo() }
    }
}
