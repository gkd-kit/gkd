package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.toAppInfo
import li.songe.gkd.permission.canQueryPkgState

val userAppInfoMapFlow = MutableStateFlow(emptyMap<String, AppInfo>())
val userAppIconMapFlow = MutableStateFlow(emptyMap<String, Drawable>())
val otherUserAppInfoMapFlow = MutableStateFlow(emptyMap<String, AppInfo>())
val otherUserAppIconMapFlow = MutableStateFlow(emptyMap<String, Drawable>())

val appInfoCacheFlow by lazy {
    combine(otherUserAppInfoMapFlow, userAppInfoMapFlow) { a, b -> a + b }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val appIconMapFlow by lazy {
    combine(userAppIconMapFlow, otherUserAppIconMapFlow) { a, b -> a + b }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

val systemAppInfoCacheFlow by lazy {
    appInfoCacheFlow.mapState(appScope) { c ->
        c.filter { a -> a.value.isSystem }
    }
}

val systemAppsFlow by lazy { systemAppInfoCacheFlow.mapState(appScope) { c -> c.keys } }

val orderedAppInfosFlow by lazy {
    appInfoCacheFlow.mapState(appScope) { c ->
        c.values.sortedWith { a, b ->
            collator.compare(a.name, b.name)
        }
    }
}

private fun Map<String, AppInfo>.getMayQueryPkgNoAccess(): Boolean {
    return values.count { a -> !a.isSystem && !a.hidden && a.id != META.appId } < MINIMUM_NORMAL_APP_SIZE
}

// https://github.com/orgs/gkd-kit/discussions/761
// 某些设备在应用更新后出现权限错乱/缓存错乱
private const val MINIMUM_NORMAL_APP_SIZE = 8
val mayQueryPkgNoAccessFlow by lazy {
    userAppInfoMapFlow.mapState(appScope) { it.getMayQueryPkgNoAccess() }
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

private fun getPkgInfo(appId: String): PackageInfo? = try {
    app.packageManager.getPackageInfo(appId, PKG_FLAGS)
} catch (_: PackageManager.NameNotFoundException) {
    null
}

val updateAppMutex = MutexState()

private suspend fun updateAppInfo(appIds: Set<String>) = updateAppMutex.withStateLock {
    willUpdateAppIds.update { it - appIds }
    val newAppMap = HashMap(userAppInfoMapFlow.value)
    val newIconMap = HashMap(userAppIconMapFlow.value)
    appIds.forEach { appId ->
        val info = getPkgInfo(appId)
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
    userAppInfoMapFlow.value = newAppMap
    userAppIconMapFlow.value = newIconMap
}

fun updateAllAppInfo(showToast: Boolean = false) = appScope.launchTry(Dispatchers.IO) {
    updateAppMutex.withStateLock {
        val newAppMap = HashMap<String, AppInfo>()
        val newIconMap = HashMap<String, Drawable>()
        val pkgList = app.packageManager.getInstalledPackages(PKG_FLAGS)
        pkgList.forEach { packageInfo ->
            newAppMap[packageInfo.packageName] = packageInfo.toAppInfo()
            packageInfo.pkgIcon?.let { icon ->
                newIconMap[packageInfo.packageName] = icon
            }
        }
        if (!canQueryPkgState.updateAndGet() || newAppMap.getMayQueryPkgNoAccess()) {
            val visiblePkgList = arrayOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW).map { action ->
                app.packageManager.queryIntentActivities(
                    Intent(action),
                    PackageManager.MATCH_DISABLED_COMPONENTS
                )
            }.flatten()
                .map { it.activityInfo.packageName }.toSet()
                .filter { !newAppMap.contains(it) }.mapNotNull { getPkgInfo(it) }
            visiblePkgList.forEach { packageInfo ->
                newAppMap[packageInfo.packageName] = packageInfo.toAppInfo()
                packageInfo.pkgIcon?.let { icon ->
                    newIconMap[packageInfo.packageName] = icon
                }
            }
        }
        userAppInfoMapFlow.value = newAppMap
        userAppIconMapFlow.value = newIconMap
        if (showToast) {
            toast("应用列表更新成功")
        }
    }
}.let { }

fun initAppState() {
    packageReceiver
    updateAllAppInfo()
    appScope.launchTry(Dispatchers.IO) {
        willUpdateAppIds.debounce(3000)
            .filter { it.isNotEmpty() }
            .collect { updateAppInfo(it) }
    }
}