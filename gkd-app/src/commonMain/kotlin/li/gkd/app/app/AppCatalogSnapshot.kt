package li.gkd.app.app

import li.gkd.app.model.AppInventory

data class AppCatalogSnapshot(
    val inventory: AppInventory,
    val launcherAppId: String? = null,
    val canQueryPackages: Boolean = true,
    val queryPackagesAbnormal: Boolean = false,
) {
    val apps = inventory.othersApps.associateBy { it.id } + inventory.apps.associateBy { it.id }
    val visibleApps = AppCatalog.visibleApps(apps.values)
    val systemApps = apps.filterValues { it.isSystem }.keys
    val users = inventory.otherUsers.associateBy { it.id }
}

/** No snapshot means no successful query yet; refresh errors retain the last complete snapshot. */
data class AppCatalogState(
    val snapshot: AppCatalogSnapshot? = null,
    val refreshing: Boolean = false,
    val failure: Throwable? = null,
)
