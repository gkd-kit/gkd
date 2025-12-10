package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.App
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.otherUserMapFlow
import li.songe.gkd.data.toAppInfo
import li.songe.gkd.data.toAppInfoAndIcon
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.shizuku.currentUserId
import li.songe.gkd.shizuku.shizukuContextFlow

val userAppInfoMapFlow = MutableStateFlow(emptyMap<String, AppInfo>())
val userAppIconMapFlow = MutableStateFlow(emptyMap<String, Drawable>())
val otherUserAppInfoMapFlow = MutableStateFlow(emptyMap<String, AppInfo>())
val otherUserAppIconMapFlow = MutableStateFlow(emptyMap<String, Drawable>())

val appInfoMapFlow by lazy {
    combine(otherUserAppInfoMapFlow, userAppInfoMapFlow) { a, b -> a + b }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val appIconMapFlow by lazy {
    combine(otherUserAppIconMapFlow, userAppIconMapFlow) { a, b -> a + b }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val systemAppInfoCacheFlow by lazy {
    appInfoMapFlow.mapState(appScope) { c ->
        c.filter { a -> a.value.isSystem }
    }
}

val systemAppsFlow by lazy { systemAppInfoCacheFlow.mapState(appScope) { c -> c.keys } }

val visibleAppInfosFlow by lazy {
    appInfoMapFlow.mapState(appScope) { c ->
        c.values.filterNot { it.hidden }.sortedWith { a, b ->
            collator.compare(a.name, b.name)
        }
    }
}

private val willUpdateAppIds by lazy { MutableStateFlow(emptySet<String>()) }

private val packageReceiver by lazy {
    object : BroadcastReceiver() {
        val actions = arrayOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED
        )

        override fun onReceive(context: Context?, intent: Intent?) {
            // PACKAGE_REMOVED->PACKAGE_ADDED->PACKAGE_REPLACED
            val appId = intent?.data?.schemeSpecificPart ?: return
            willUpdateAppIds.update { it + appId }
        }
    }.apply {
        val intentFilter = IntentFilter().apply {
            actions.forEach { addAction(it) }
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            app,
            this,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}

const val PKG_FLAGS = PackageManager.MATCH_UNINSTALLED_PACKAGES

val updateAppMutex = MutexState()

private fun updateOtherUserAppInfo(userAppInfoMap: Map<String, AppInfo>? = null) {
    val pkgManager = shizukuContextFlow.value.packageManager
    val userManager = shizukuContextFlow.value.userManager
    val actualUserAppInfoMap = userAppInfoMap ?: userAppInfoMapFlow.value
    if (pkgManager == null || userManager == null || actualUserAppInfoMap.isEmpty()) {
        otherUserMapFlow.value = emptyMap()
        otherUserAppIconMapFlow.value = emptyMap()
        otherUserAppInfoMapFlow.value = emptyMap()
        return
    }
    val otherUsers = userManager.getUsers().filter { it.id != currentUserId }.sortedBy { it.id }
    val userPackageInfoMap = otherUsers.associate { user ->
        user.id to pkgManager.getInstalledPackages(
            PKG_FLAGS,
            user.id
        ).filterNot { actualUserAppInfoMap.contains(it.packageName) }
    }
    val newIconMap = HashMap<String, Drawable>()
    val newAppMap = HashMap<String, AppInfo>()
    userPackageInfoMap.forEach { (userId, pkgInfoList) ->
        pkgInfoList.forEach { pkgInfo ->
            if (!newAppMap.contains(pkgInfo.packageName)) {
                val (appInfo, appIcon) = pkgInfo.toAppInfoAndIcon(userId)
                newAppMap[pkgInfo.packageName] = appInfo
                if (appIcon != null) {
                    newIconMap[pkgInfo.packageName] = appIcon
                }
            }
        }
    }
    otherUserMapFlow.value = otherUsers.associateBy { it.id }
    otherUserAppInfoMapFlow.value = newAppMap
    otherUserAppIconMapFlow.value = newIconMap
}

private fun updatePartAppInfo(
    appIds: Set<String>,
) = updateAppMutex.launchTry(appScope, Dispatchers.IO) {
    willUpdateAppIds.update { it - appIds }
    val newAppMap = HashMap(userAppInfoMapFlow.value)
    val newIconMap = HashMap(userAppIconMapFlow.value)
    appIds.forEach { appId ->
        val info = app.getPkgInfo(appId)
        if (info != null) {
            newAppMap[appId] = info.toAppInfo()
        } else {
            newAppMap.remove(appId)
        }
        val icon = info?.pkgIcon
        if (icon != null) {
            newIconMap[appId] = icon
        } else {
            newIconMap.remove(appId)
        }
    }
    updateOtherUserAppInfo(newAppMap)
    userAppInfoMapFlow.value = newAppMap
    userAppIconMapFlow.value = newIconMap
}

val appListAuthAbnormalFlow = MutableStateFlow(false)

fun updateAllAppInfo(): Unit = updateAppMutex.launchTry(appScope, Dispatchers.IO) {
    val newAppMap = HashMap<String, AppInfo>()
    val newIconMap = HashMap<String, Drawable>()
    // see #1169 DeadObjectException BadParcelableException
    val pkgList = app.packageManager.getInstalledPackages(PKG_FLAGS)
    pkgList.forEach { pkgInfo ->
        val (appInfo, appIcon) = pkgInfo.toAppInfoAndIcon()
        newAppMap[pkgInfo.packageName] = appInfo
        if (appIcon != null) {
            newIconMap[pkgInfo.packageName] = appIcon
        }
    }
    val mayAuthDenied = newAppMap.count { !it.value.isSystem } <= 4
    canQueryPkgState.updateAndGet()
    appListAuthAbnormalFlow.value = canQueryPkgState.value && mayAuthDenied
    if (!canQueryPkgState.value || mayAuthDenied) {
        LogUtils.d(
            "updateAllAppInfo",
            "mayAuthDenied=$mayAuthDenied, newAppMap.size=${newAppMap.size}"
        )
        val pkgList2 = shizukuContextFlow.value.packageManager?.getInstalledPackages(PKG_FLAGS)
        if (!pkgList2.isNullOrEmpty()) {
            pkgList2.forEach { pkgInfo ->
                val (appInfo, appIcon) = pkgInfo.toAppInfoAndIcon()
                newAppMap[pkgInfo.packageName] = appInfo
                if (appIcon != null) {
                    newIconMap[pkgInfo.packageName] = appIcon
                }
            }
        } else {
            val visiblePkgList = arrayOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW).map { action ->
                try {
                    // DeadObjectException BadParcelableException
                    app.packageManager.queryIntentActivities(
                        Intent(action),
                        PackageManager.MATCH_DISABLED_COMPONENTS
                    )
                } catch (_: Throwable) {
                    emptyList()
                }
            }.flatten()
                .map { it.activityInfo.packageName }.toSet()
                .filter { !newAppMap.contains(it) }.mapNotNull { app.getPkgInfo(it) }
            visiblePkgList.forEach { pkgInfo ->
                val (appInfo, appIcon) = pkgInfo.toAppInfoAndIcon(hidden = false)
                newAppMap[pkgInfo.packageName] = appInfo
                if (appIcon != null) {
                    newIconMap[pkgInfo.packageName] = appIcon
                }
            }
        }
    }
    updateOtherUserAppInfo(newAppMap)
    userAppInfoMapFlow.value = newAppMap
    userAppIconMapFlow.value = newIconMap
    if (!app.justStarted && isActivityVisible()) {
        toast("应用列表更新成功")
    }
    if (canQueryPkgState.value && mayAuthDenied && app.justStarted) {
        // 概率出现：即使有「读取应用列表权限」在刚启动时也只能获取到少量应用，延迟几秒再试一次
        appScope.launch {
            delay(App.START_WAIT_TIME)
            updateAllAppInfo()
        }
    }
}

fun initAppState() {
    packageReceiver
    updateAllAppInfo()
    appScope.launchTry {
        shizukuContextFlow.drop(1).collect {
            updateAppMutex.launchTry(appScope, Dispatchers.IO) {
                updateOtherUserAppInfo()
            }
        }
    }
    appScope.launchTry {
        willUpdateAppIds.debounce(3000)
            .filter { it.isNotEmpty() }
            .collect { updatePartAppInfo(it) }
    }
}