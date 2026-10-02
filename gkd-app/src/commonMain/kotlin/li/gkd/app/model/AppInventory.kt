package li.gkd.app.model

import kotlinx.serialization.Serializable
import li.gkd.app.app.AppCatalog

@Serializable
data class AppInventory(
    val userId: Int = 0,
    val apps: List<AppInfo>,
    val otherUsers: List<AppUser> = emptyList(),
    val othersApps: List<AppInfo> = emptyList(),
) {
    // Primary profile wins when a package is installed in more than one profile.
    val appsById: Map<String, AppInfo>
        get() = AppCatalog.normalize(this).let {
            it.othersApps.associateBy { app -> app.id } + it.apps.associateBy { app -> app.id }
        }
}

@Serializable
data class AppUser(val id: Int, val name: String?)
