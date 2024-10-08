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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

private val packageReceiver by lazy {
    object : BroadcastReceiver() {
        /**
         * 例: 小米应用商店更新应用产生连续 3个事件: PACKAGE_REMOVED->PACKAGE_ADDED->PACKAGE_REPLACED
         *
         */
        override fun onReceive(context: Context?, intent: Intent?) {
            val appId = intent?.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED || intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                // update
                updateAppInfo(appId)
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


private val updateAppMutex by lazy { Mutex() }

private fun updateAppInfo(appId: String) {
    appScope.launchTry(Dispatchers.IO) {
        val packageManager = app.packageManager
        updateAppMutex.withLock {
            val newMap = appInfoCacheFlow.value.toMutableMap()
            val info = try {
                packageManager.getPackageInfo(appId, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
            if (info != null) {
                newMap[appId] = info.toAppInfo()
            } else {
                newMap.remove(appId)
            }
            appInfoCacheFlow.value = newMap
        }
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
    }
}