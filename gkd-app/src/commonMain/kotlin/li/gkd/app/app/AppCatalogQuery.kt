package li.gkd.app.app

import li.gkd.app.model.AppInfo
import li.gkd.app.model.AppUser
import li.gkd.app.platform.PlatformResult

/** A query captures one host environment; it must not switch privilege sessions mid-query. */
interface AppCatalogQuery {
    val userId: Int
    val launcherAppId: String?
    val canQueryPackages: Boolean
    val detectIncompleteList: Boolean get() = true
    val queryPackagesAbnormal: Boolean get() = false
    fun isCurrent(): Boolean = true

    /** Release obsolete host resources after a successful query. Must not throw. */
    fun committed(snapshot: AppCatalogSnapshot) = Unit
    suspend fun primaryApps(): List<AppInfo>

    /** null means confirmed absence, never a failed query. */
    suspend fun primaryApp(id: String): AppInfo?
    suspend fun privilegedApps(userId: Int): PlatformResult<List<AppInfo>>
    suspend fun visibleApps(): List<AppInfo>
    suspend fun otherUsers(): List<AppUser>
}

/** Opens a platform query session; aggregation and publication stay in AppInfoRepository. */
expect suspend fun openAppCatalog(): AppCatalogQuery
