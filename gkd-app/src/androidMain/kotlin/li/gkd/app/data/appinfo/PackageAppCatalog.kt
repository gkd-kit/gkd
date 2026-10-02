package li.gkd.app.data.appinfo

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.app.AppCatalogQuery
import li.gkd.app.app.AppCatalogSnapshot
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.appScope
import li.gkd.app.data.toAppInfo
import li.gkd.app.model.AppInfo
import li.gkd.app.permission.PermissionStates
import li.gkd.app.platform.PlatformResult
import li.gkd.app.priv.currentUserId
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.util.LogUtils
import li.gkd.app.util.pkgIcon
import java.util.concurrent.ConcurrentHashMap

/** Android package collection and resources only; all merge/refresh policy lives in shared. */
object PackageAppCatalog {
    val selfAppInfo by lazy {
        app.packageManager.getPackageInfo(app.packageName, 0).toAppInfo()
    }

    const val packageFlags = PackageManager.MATCH_UNINSTALLED_PACKAGES
    private val icons = ConcurrentHashMap<AppInfo, Drawable>()
    fun icon(info: AppInfo?): Drawable? = info?.let(icons::get)

    suspend fun open(): AppCatalogQuery {
        val privilege = privilegeContextFlow.value
        val allowed = PermissionStates.queryPackages.updateAndGet()
        return object : AppCatalogQuery {
            override val userId = currentUserId
            override val launcherAppId: String? = null
            override val canQueryPackages = allowed
            override fun committed(snapshot: AppCatalogSnapshot) {
                icons.keys.retainAll(snapshot.apps.values.toSet())
            }

            override fun isCurrent() = privilegeContextFlow.value === privilege &&
                    PermissionStates.queryPackages.value == allowed

            private fun convert(
                info: PackageInfo,
                user: Int = userId,
                hidden: Boolean? = null
            ): AppInfo {
                val result = info.toAppInfo(user, hidden)
                if (!result.hidden) {
                    try {
                        val icon = info.pkgIcon
                        if (icon == null) icons.remove(result) else icons[result] = icon
                    } catch (e: Exception) {
                        LogUtils.d("app icon query failed", e)
                    }
                }
                return result
            }

            override suspend fun primaryApps() =
                app.packageManager.getInstalledPackages(packageFlags).map { convert(it) }

            override suspend fun primaryApp(id: String) = app.getPkgInfo(id)?.let { convert(it) }
            override suspend fun privilegedApps(userId: Int): PlatformResult<List<AppInfo>> =
                privilege?.let { context ->
                    PlatformResult.Success(
                        context.getInstalledPackagesAsUser(packageFlags, userId)
                            .map { convert(it, userId) })
                } ?: PlatformResult.Unsupported

            override suspend fun otherUsers() = privilege?.getUsers().orEmpty()
            override suspend fun visibleApps(): List<AppInfo> =
                arrayOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW).asSequence().flatMap { action ->
                    app.packageManager.queryIntentActivities(
                        Intent(action),
                        PackageManager.MATCH_DISABLED_COMPONENTS
                    )
                }.map { it.activityInfo.packageName }.toSet().mapNotNull { app.getPkgInfo(it) }
                    .map { convert(it, hidden = false) }.toList()
        }
    }

    fun initialize() {
        AppChangeMonitor.register { AppInfoRepository.packagesChanged(setOf(it)) }
        AppInfoRepository.requestRefresh()
        appScope.launch(Dispatchers.IO) {
            privilegeContextFlow.drop(1).collect { AppInfoRepository.privilegeChanged() }
        }
    }
}
