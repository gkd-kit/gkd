package li.gkd.app.permission

enum class AppPermission {
    LocalNetwork,
    IgnoreBatteryOptimizations,
    QueryPackages,
}

interface PermissionRequester {
    /** Requests permissions in order; returns false when a required permission is not granted. */
    suspend fun ensurePermissions(vararg permissions: AppPermission): Boolean
}
