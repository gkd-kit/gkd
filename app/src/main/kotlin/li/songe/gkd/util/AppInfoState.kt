package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.toAppInfo

val appInfoCacheFlow = MutableStateFlow(emptyMap<String, AppInfo>())

val systemAppInfoCacheFlow by lazy {
    appInfoCacheFlow.map(appScope) { c ->
        c.filter { a -> a.value.isSystem }
    }
}

val systemAppsFlow by lazy { systemAppInfoCacheFlow.map(appScope) { c -> c.keys } }

val orderedAppInfosFlow by lazy {
    appInfoCacheFlow.map(appScope) { c ->
        c.values.sortedWith { a, b ->
            collator.compare(a.name, b.name)
        }
    }
}

// https://github.com/orgs/gkd-kit/discussions/761
// 某些设备在应用更新后出现权限错乱/缓存错乱
private const val MINIMUM_NORMAL_APP_SIZE = 8
val mayQueryPkgNoAccessFlow by lazy {
    appInfoCacheFlow.map(appScope) { c ->
        c.values.count { a -> !a.isSystem && !a.hidden && a.id != META.appId } < MINIMUM_NORMAL_APP_SIZE
    }
}

private val willUpdateAppIds by lazy { MutableStateFlow(emptySet<String>()) }

private val packageReceiver by lazy {
    object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val appId = intent?.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED || intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                /**
                 * 例: 小米应用商店更新应用产生连续 3个事件: PACKAGE_REMOVED->PACKAGE_ADDED->PACKAGE_REPLACED
                 * 使用 Flow + debounce 优化合并
                 */
                willUpdateAppIds.update { it + appId }
            }
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(
                this,
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                },
            )
        }
    }
}

private fun getAppInfo(appId: String): AppInfo? {
    return try {
        app.packageManager.getPackageInfo(appId, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }?.toAppInfo()
}

private val updateAppMutex by lazy { Mutex() }

private suspend fun updateAppInfo(appIds: Set<String>) {
    if (appIds.isEmpty()) return
    willUpdateAppIds.update { it - appIds }
    updateAppMutex.withLock {
        LogUtils.d("updateAppInfo", appIds)
        val newMap = appInfoCacheFlow.value.toMutableMap()
        appIds.forEach { appId ->
            val info = getAppInfo(appId)
            if (info != null) {
                newMap[appId] = info
            } else {
                newMap.remove(appId)
            }
        }
        appInfoCacheFlow.value = newMap
    }
}

val appRefreshingFlow = MutableStateFlow(false)

suspend fun initOrResetAppInfoCache() {
    if (updateAppMutex.isLocked) return
    LogUtils.d("initOrResetAppInfoCache start")
    appRefreshingFlow.value = true
    updateAppMutex.withLock {
        val oldAppIds = appInfoCacheFlow.value.keys
        val appMap = appInfoCacheFlow.value.toMutableMap()
        withContext(Dispatchers.IO) {
            app.packageManager.getInstalledPackages(0).forEach { packageInfo ->
                if (!oldAppIds.contains(packageInfo.packageName)) {
                    appMap[packageInfo.packageName] = packageInfo.toAppInfo()
                }
            }
        }
        appInfoCacheFlow.value = appMap
    }
    appRefreshingFlow.value = false
    LogUtils.d("initOrResetAppInfoCache end")
}

fun initAppState() {
    packageReceiver
    appScope.launchTry(Dispatchers.IO) {
        initOrResetAppInfoCache()
        willUpdateAppIds.debounce(1000)
            .filter { it.isNotEmpty() }
            .collect { updateAppInfo(it) }
    }
}