package li.songe.gkd.shizuku


import android.app.IActivityTaskManager
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.IUserManager
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.otherUserMapFlow
import li.songe.gkd.data.toAppInfo
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.store.shizukuStoreFlow
import li.songe.gkd.util.allPackageInfoMapFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.otherUserAppInfoMapFlow
import li.songe.gkd.util.toast
import li.songe.gkd.util.userAppInfoMapFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

private fun getStubService(name: String): ShizukuBinderWrapper? {
    val service = SystemServiceHelper.getSystemService(name)
    if (service == null) {
        LogUtils.d("获取 $name 失败")
        return null
    }
    return ShizukuBinderWrapper(service)
}

private fun newUserManager() = getStubService(Context.USER_SERVICE)?.let {
    SafeUserManager(IUserManager.Stub.asInterface(it))
}

private fun newActivityTaskManager() = getStubService("activity_task")?.let {
    SafeActivityTaskManager(IActivityTaskManager.Stub.asInterface(it))
}

private fun newPackageManager() = getStubService("package")?.let {
    SafePackageManager(IPackageManager.Stub.asInterface(it))
}

private val shizukuActivityUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, shizukuStoreFlow) { shizukuOk, store ->
        shizukuOk && store.enableActivity
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val userManagerFlow by lazy<StateFlow<SafeUserManager?>> {
    val stateFlow = MutableStateFlow<SafeUserManager?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuWorkProfileUsedFlow.collect {
            stateFlow.value = if (it) newUserManager() else null
        }
    }
    stateFlow
}

val activityTaskManagerFlow by lazy<StateFlow<SafeActivityTaskManager?>> {
    val stateFlow = MutableStateFlow<SafeActivityTaskManager?>(null)
    appScope.launchTry(Dispatchers.IO) {
        shizukuActivityUsedFlow.collect {
            if (shizukuOkState.value) {
                stateFlow.value?.unregisterTaskStackListener(MyTaskListener)
            }
            stateFlow.value = if (it) newActivityTaskManager() else null
            stateFlow.value?.registerTaskStackListener(MyTaskListener)
        }
    }
    stateFlow
}

val shizukuWorkProfileUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, shizukuStoreFlow) { shizukuOk, store ->
        shizukuOk && store.enableWorkProfile
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val packageManagerFlow by lazy<StateFlow<SafePackageManager?>> {
    val stateFlow = MutableStateFlow<SafePackageManager?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuWorkProfileUsedFlow.collect {
            stateFlow.value = if (it) newPackageManager() else null
        }
    }
    stateFlow
}

fun shizukuCheckActivity(): Boolean {
    return (try {
        newActivityTaskManager()?.compatGetTasks()?.isNotEmpty() == true
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    })
}

fun shizukuCheckGranted(): Boolean {
    val granted = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
    if (!granted) return false
    if (shizukuStoreFlow.value.enableActivity) {
        return safeGetTopCpn() != null || shizukuCheckActivity()
    }
    return true
}

fun shizukuCheckWorkProfile(): Boolean {
    return (try {
        arrayOf(
            newPackageManager()?.getAllIntentFilters(META.appId)?.isNotEmpty() == true,
            newUserManager()?.compatGetUsers()?.isNotEmpty() == true
        ).all { it }
    } catch (e: Throwable) {
        e.printStackTrace()
        false
    })
}

fun safeGetTopCpn() = try {
    activityTaskManagerFlow.value?.getTopCpn()
} catch (_: Throwable) {
    null
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
        val prefix = if (isActivityVisible()) "" else "${META.appName}: "
        toast("${prefix}已断开 Shizuku 服务")
    }
    serviceWrapperFlow.value
    appScope.launchTry(Dispatchers.IO) {
        combine(
            packageManagerFlow,
            userManagerFlow,
        ) { a, b -> a to b }.collect { (pkgManager, userManager) ->
            if (pkgManager != null && userManager != null) {
                val otherUsers = userManager.compatGetUsers()
                    .filter { it.id != 0 }.sortedBy { it.id }
                otherUserMapFlow.value = otherUsers.associateBy { it.id }
                allPackageInfoMapFlow.value = otherUsers
                    .map {
                        it.id to pkgManager.compatGetInstalledPackages(
                            PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong(),
                            it.id
                        )
                    }
                    .associate { it }
            } else {
                otherUserMapFlow.value = emptyMap()
                allPackageInfoMapFlow.value = emptyMap()
            }
        }
    }
    appScope.launchTry(Dispatchers.IO) {
        combine(
            packageManagerFlow,
            userAppInfoMapFlow,
            allPackageInfoMapFlow,
        ) { a, b, c -> Triple(a, b, c) }.debounce(3000)
            .collect { (pkgManager, userAppInfoMap, allPackageInfoMap) ->
                otherUserAppInfoMapFlow.update {
                    if (pkgManager != null) {
                        val map = HashMap<String, AppInfo>()
                        allPackageInfoMap.forEach { (userId, pkgInfoList) ->
                            val diffPkgList = pkgInfoList.filter {
                                !userAppInfoMap.contains(it.packageName) && !map.contains(
                                    it.packageName
                                )
                            }
                            diffPkgList.forEach { pkgInfo ->
                                val hidden =
                                    !pkgManager.getAllIntentFilters(pkgInfo.packageName).any { f ->
                                        f.hasAction(Intent.ACTION_MAIN) && f.hasCategory(
                                            Intent.CATEGORY_LAUNCHER
                                        )
                                    }
                                map[pkgInfo.packageName] = pkgInfo.toAppInfo(
                                    userId = userId,
                                    hidden = hidden,
                                )
                            }
                        }
                        map
                    } else {
                        emptyMap()
                    }
                }
            }
    }
}
