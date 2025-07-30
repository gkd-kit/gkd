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
import li.songe.gkd.permission.canQueryPkgState
import kotlin.time.Duration.Companion.days

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

private fun Map<String, AppInfo>.getMayQueryPkgNoAccess(): Boolean {
    return values.count { a -> !a.isSystem && !a.hidden && a.id != META.appId } < MINIMUM_NORMAL_APP_SIZE
}

// https://github.com/orgs/gkd-kit/discussions/761
// 某些设备在应用更新后出现权限错乱/缓存错乱
private const val MINIMUM_NORMAL_APP_SIZE = 8
val mayQueryPkgNoAccessFlow by lazy {
    userAppInfoMapFlow.map(appScope) { it.getMayQueryPkgNoAccess() }
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

fun getPkgInfo(appId: String): PackageInfo? {
    return try {
        app.packageManager.getPackageInfo(appId, PackageManager.MATCH_UNINSTALLED_PACKAGES)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

val updateAppMutex = MutexState()
private var lastUpdateAppListTime = 0L

private suspend fun updateAppInfo(appIds: Set<String>) {
    if (appIds.isEmpty()) return
    willUpdateAppIds.update { it - appIds }
    updateAppMutex.withLock {
        LogUtils.d("updateAppInfo", appIds)
        val newMap = userAppInfoMapFlow.value.toMutableMap()
        appIds.forEach { appId ->
            val info = getPkgInfo(appId)
            if (info != null) {
                newMap[appId] = info.toAppInfo()
            } else {
                newMap.remove(appId)
            }
        }
        userAppInfoMapFlow.value = newMap
    }
}

suspend fun initOrResetAppInfoCache() = updateAppMutex.withLock {
    LogUtils.d("initOrResetAppInfoCache start")
    val oldMap = userAppInfoMapFlow.value
    val appMap = userAppInfoMapFlow.value.toMutableMap()
    withContext(Dispatchers.IO) {
        app.packageManager.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .forEach { packageInfo ->
                appMap[packageInfo.packageName] = packageInfo.toAppInfo()
            }
    }
    if (!canQueryPkgState.updateAndGet() || appMap.getMayQueryPkgNoAccess()) {
        withContext(Dispatchers.IO) {
            arrayOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW).map { action ->
                app.packageManager.queryIntentActivities(
                    Intent(action),
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }.flatten().map { it.activityInfo.packageName }.toSet().forEach { appId ->
                if (!appMap.contains(appId)) {
                    getPkgInfo(appId)?.let {
                        appMap[appId] = it.toAppInfo()
                    }
                }
            }
        }
    }
    userAppInfoMapFlow.value = appMap
    lastUpdateAppListTime = System.currentTimeMillis()
    LogUtils.d("initOrResetAppInfoCache end ${oldMap.size}->${appMap.size}")
}


fun forceUpdateAppList() {
    if (updateAppMutex.mutex.isLocked) return
    val interval = System.currentTimeMillis() - lastUpdateAppListTime
    if (interval > 7.days.inWholeMilliseconds) {
        // 每 7 天强制更新一次应用列表数据
        appScope.launchTry(Dispatchers.IO) { initOrResetAppInfoCache() }
    }
}

fun initAppState() {
    packageReceiver
    appScope.launchTry(Dispatchers.IO) {
        willUpdateAppIds.debounce(3000)
            .filter { it.isNotEmpty() }
            .collect { updateAppInfo(it) }
    }
}