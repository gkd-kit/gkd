package li.songe.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.blankj.utilcode.util.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.AppInfo
import li.songe.gkd.util.Ext.getApplicationInfoExt


private val _appInfoCacheFlow = MutableStateFlow(mapOf<String, AppInfo>())

val appInfoCacheFlow: StateFlow<Map<String, AppInfo>>
    get() = _appInfoCacheFlow

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


private fun getAppInfo(id: String): AppInfo? {
    val packageManager = app.packageManager
    val info = try { //        需要权限
        val rawInfo = app.packageManager.getApplicationInfoExt(
            id, PackageManager.GET_META_DATA
        )
        val info = AppUtils.getAppInfo(id) ?: return null
        AppInfo(
            id = id,
            name = packageManager.getApplicationLabel(rawInfo).toString(),
            icon = packageManager.getApplicationIcon(rawInfo),
            versionCode = info.versionCode,
            versionName = info.versionName
        )
    } catch (e: Exception) {
        return null
    }
    return info
}

val mutex by lazy { Mutex() }

fun updateAppInfo(vararg appIds: String) {
    if (appIds.isEmpty()) return
    appScope.launchTry(Dispatchers.IO) {
        mutex.withLock {
            val newMap = _appInfoCacheFlow.value.toMutableMap()
            appIds.forEach { appId ->
                val newAppInfo = getAppInfo(appId)
                if (newAppInfo != null) {
                    newMap[appId] = newAppInfo
                } else {
                    newMap.remove(appId)
                }
            }
            _appInfoCacheFlow.value = newMap
        }
    }
}


fun initAppState() {
    packageReceiver
}