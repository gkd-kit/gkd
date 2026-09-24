package li.gkd.app.data.appinfo

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.App
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.data.AppInfo
import li.gkd.app.data.UserInfo
import li.gkd.app.data.toAppInfo
import li.gkd.app.data.toAppInfoAndIcon
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.currentUserId
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.util.LogUtils
import li.gkd.app.util.MutexState
import li.gkd.app.util.collator
import li.gkd.app.util.launchLogged
import li.gkd.app.util.mapState
import li.gkd.app.util.pkgIcon
import kotlin.time.Duration.Companion.milliseconds

object AppInfoRepository {
    val userAppInfoMapFlow: StateFlow<Map<String, AppInfo>>
        field = MutableStateFlow(emptyMap())
    val userAppIconMapFlow: StateFlow<Map<String, Drawable>>
        field = MutableStateFlow(emptyMap())
    val otherUserMapFlow: StateFlow<Map<Int, UserInfo>>
        field = MutableStateFlow(emptyMap())
    val otherUserAppInfoMapFlow: StateFlow<Map<String, AppInfo>>
        field = MutableStateFlow(emptyMap())
    val otherUserAppIconMapFlow: StateFlow<Map<String, Drawable>>
        field = MutableStateFlow(emptyMap())

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

    private val willUpdateAppIds = MutableStateFlow(emptySet<String>())

    private fun dispatchAppUpdate(appId: String) = willUpdateAppIds.update { it + appId }

    val packageFlags = PackageManager.MATCH_UNINSTALLED_PACKAGES

    private val updateAppMutex = MutexState()
    val updating = updateAppMutex.state

    private fun updateOtherUserAppInfo(userAppInfoMap: Map<String, AppInfo>? = null) {
        val privilegeContext = privilegeContextFlow.value
        val actualUserAppInfoMap = userAppInfoMap ?: userAppInfoMapFlow.value
        if (privilegeContext == null || actualUserAppInfoMap.isEmpty()) {
            otherUserMapFlow.value = emptyMap()
            otherUserAppIconMapFlow.value = emptyMap()
            otherUserAppInfoMapFlow.value = emptyMap()
            return
        }
        val otherUsers = privilegeContext.getUsers()
            .filter { it.id != currentUserId }
            .sortedBy { it.id }
        val userPackageInfoMap = otherUsers.associate { user ->
            user.id to privilegeContext.getInstalledPackagesAsUser(
                packageFlags,
                user.id,
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

    private suspend fun updatePartAppInfo(
        appIds: Set<String>,
    ) = updateAppMutex.withStateLock {
        willUpdateAppIds.update { it - appIds }
        val newAppMap = HashMap(userAppInfoMapFlow.value)
        val newIconMap = HashMap(userAppIconMapFlow.value)
        val oldMapSize = newAppMap.size
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
        LogUtils.d(
            "updatePartAppInfo",
            "change=${appIds.map { (if (newAppMap.contains(it)) "+" else "-") + it }}",
            "size=${oldMapSize}->${newAppMap.size}"
        )
    }

    val appListAuthAbnormalFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    suspend fun refresh() = withContext(Dispatchers.IO) {
            updateAppMutex.withStateLock {
                val newAppMap = HashMap<String, AppInfo>()
                val newIconMap = HashMap<String, Drawable>()
                // see #1169 DeadObjectException BadParcelableException
                val pkgList = app.packageManager.getInstalledPackages(packageFlags)
                pkgList.forEach { pkgInfo ->
                    val (appInfo, appIcon) = pkgInfo.toAppInfoAndIcon()
                    newAppMap[pkgInfo.packageName] = appInfo
                    if (appIcon != null) {
                        newIconMap[pkgInfo.packageName] = appIcon
                    }
                }
                val mayAuthDenied = newAppMap.count { !it.value.isSystem } <= 4
                PermissionStates.queryPackages.updateAndGet()
                appListAuthAbnormalFlow.value =
                    PermissionStates.queryPackages.value && mayAuthDenied
                if (!PermissionStates.queryPackages.value || mayAuthDenied) {
                    LogUtils.d(
                        "updateAllAppInfo",
                        "mayAuthDenied=$mayAuthDenied, newAppMap.size=${newAppMap.size}"
                    )
                    val pkgList2 = privilegeContextFlow.value
                        ?.getInstalledPackagesAsUser(packageFlags, currentUserId)
                    if (!pkgList2.isNullOrEmpty()) {
                        pkgList2.forEach { pkgInfo ->
                            val (appInfo, appIcon) = pkgInfo.toAppInfoAndIcon()
                            newAppMap[pkgInfo.packageName] = appInfo
                            if (appIcon != null) {
                                newIconMap[pkgInfo.packageName] = appIcon
                            }
                        }
                    } else {
                        val visiblePkgList =
                            arrayOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW)
                                .asSequence()
                                .flatMap { action ->
                                    try {
                                        // DeadObjectException BadParcelableException
                                        app.packageManager.queryIntentActivities(
                                            Intent(action),
                                            PackageManager.MATCH_DISABLED_COMPONENTS
                                        )
                                    } catch (_: Throwable) {
                                        emptyList()
                                    }
                                }
                                .map { it.activityInfo.packageName }
                                .toSet()
                                .filter { !newAppMap.contains(it) }
                                .mapNotNull { app.getPkgInfo(it) }
                                .toList()
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
                if (PermissionStates.queryPackages.value && mayAuthDenied && app.justStarted) {
                    // Probabilistic occurrence: Even with "Read app list permission", only a few apps can be obtained at startup; delay a few seconds and try again.
                    appScope.launch {
                        delay(App.START_WAIT_TIME.milliseconds)
                        requestRefresh()
                    }
                }
            }
    }

    fun requestRefresh() {
        appScope.launchLogged(Dispatchers.IO) { refresh() }
    }

    fun initialize() {
        AppChangeMonitor.register(::dispatchAppUpdate)
        requestRefresh()
        appScope.launchLogged(Dispatchers.IO) {
            privilegeContextFlow.drop(1).collect {
                try {
                    updateAppMutex.withStateLock {
                        updateOtherUserAppInfo()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtils.d("update other user app info failed", e)
                }
            }
        }
        appScope.launchLogged(Dispatchers.IO) {
            willUpdateAppIds.debounce(3000.milliseconds)
                .filter { it.isNotEmpty() }
                .collect { appIds ->
                    try {
                        updatePartAppInfo(appIds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        LogUtils.d("update app info failed", e)
                    }
                }
        }
    }
}
