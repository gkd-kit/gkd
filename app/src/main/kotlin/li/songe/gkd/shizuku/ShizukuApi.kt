package li.songe.gkd.shizuku


import android.content.Intent
import android.content.pm.PackageManager
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
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

fun shizukuCheckGranted(): Boolean {
    val granted = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
    if (!granted) return false
    if (shizukuStoreFlow.value.enableActivity) {
        return safeGetTopActivity() != null || shizukuCheckActivity()
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
