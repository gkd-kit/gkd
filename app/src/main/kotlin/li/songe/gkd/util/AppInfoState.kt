package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.blankj.utilcode.util.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo

val appInfoCacheFlow = MutableStateFlow(mapOf<String, AppInfo>())

val systemAppInfoCacheFlow =
    appInfoCacheFlow.map(appScope) { c -> c.filter { a -> a.value.isSystem } }

val systemAppsFlow = systemAppInfoCacheFlow.map(appScope) { c -> c.keys }

val orderedAppInfosFlow = appInfoCacheFlow.map(appScope) { c ->
    c.values.sortedWith { a, b ->
        collator.compare(
            if (a.isSystem) {
                "\uFFFF${a.name}"
            } else {
                a.name
            }, if (b.isSystem) {
                "\uFFFF${b.name}"
            } else {
                b.name
            }
        )
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
                updateAppInfo(listOf(appId))
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

fun updateAppInfo(appIds: List<String>) {
    if (appIds.isEmpty()) return
    appScope.launchTry(Dispatchers.IO) {
        updateAppMutex.withLock {
            val newMap = appInfoCacheFlow.value.toMutableMap()
            appIds.forEach { appId ->
                val a = AppUtils.getAppInfo(appId)
                if (a != null) {
                    val newAppInfo = AppInfo(
                        id = a.packageName,
                        name = a.name,
                        icon = a.icon,
                        versionCode = a.versionCode,
                        versionName = a.versionName,
                        isSystem = a.isSystem,
                    )
                    newMap[appId] = newAppInfo
                } else {
                    newMap.remove(appId)
                }
            }
            appInfoCacheFlow.value = newMap
        }
    }
}


fun initAppState() {
    packageReceiver
    appScope.launchTry(Dispatchers.IO) {
        updateAppMutex.withLock {
            val appMap = mutableMapOf<String, AppInfo>()
            AppUtils.getAppsInfo().forEach { a ->
                appMap[a.packageName] = AppInfo(
                    id = a.packageName,
                    name = a.name,
                    icon = a.icon,
                    versionCode = a.versionCode,
                    versionName = a.versionName,
                    isSystem = a.isSystem,
                )
            }
            appInfoCacheFlow.value = appMap
        }
    }
}