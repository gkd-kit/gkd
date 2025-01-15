package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.toAppInfo

val userAppInfoMapFlow = MutableStateFlow(emptyMap<String, AppInfo>())
val allPackageInfoMapFlow = MutableStateFlow(emptyMap<Int, List<PackageInfo>>())
val otherUserAppInfoMapFlow = MutableStateFlow(emptyMap<String, AppInfo>())
val appInfoCacheFlow by lazy {
    combine(otherUserAppInfoMapFlow, userAppInfoMapFlow) { a, b -> a + b }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
}

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
    userAppInfoMapFlow.map(appScope) { c ->
        c.values.count { a -> !a.isSystem && !a.hidden && a.id != META.appId } < MINIMUM_NORMAL_APP_SIZE
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
            val appId = intent?.data?.schemeSpecificPart ?: return
            if (actions.contains(intent.action)) {
                /**
                 * 例: 小米应用商店更新应用产生连续 3个事件: PACKAGE_REMOVED->PACKAGE_ADDED->PACKAGE_REPLACED
                 * 使用 Flow + debounce 优化合并
                 */
                willUpdateAppIds.update { it + appId }
            }
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

private fun getAppInfo(appId: String): AppInfo? {
    return try {
        // flags=0 : only get basic info
        app.packageManager.getPackageInfo(appId, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }?.toAppInfo()
}

val updateAppMutex = MutexState()

private suspend fun updateAppInfo(appIds: Set<String>) {
    if (appIds.isEmpty()) return
    willUpdateAppIds.update { it - appIds }
    updateAppMutex.withLock {
        LogUtils.d("updateAppInfo", appIds)
        val newMap = userAppInfoMapFlow.value.toMutableMap()
        appIds.forEach { appId ->
            val info = getAppInfo(appId)
            if (info != null) {
                newMap[appId] = info
            } else {
                newMap.remove(appId)
            }
        }
        userAppInfoMapFlow.value = newMap
    }
}

suspend fun initOrResetAppInfoCache() = updateAppMutex.withLock {
    LogUtils.d("initOrResetAppInfoCache start")
    val oldAppIds = userAppInfoMapFlow.value.keys
    val appMap = userAppInfoMapFlow.value.toMutableMap()
    withContext(Dispatchers.IO) {
        app.packageManager.getInstalledPackages(0)
            .forEach { packageInfo ->
                if (!oldAppIds.contains(packageInfo.packageName)) {
                    appMap[packageInfo.packageName] = packageInfo.toAppInfo()
                }
            }
    }
    userAppInfoMapFlow.value = appMap
    LogUtils.d("initOrResetAppInfoCache end ${oldAppIds.size}->${appMap.size}")
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