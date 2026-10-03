package li.gkd.app.priv

/** Permission checks for one connected server, separate from permissions held by GKD itself. */
data class PrivilegeCapabilities(
    val grantRuntimePermissions: Boolean,
    val injectEvents: Boolean,
    val writeSecureSettings: Boolean,
    val updateAppOps: Boolean,
) {
    val restricted: Boolean
        get() = !grantRuntimePermissions || !injectEvents || !writeSecureSettings || !updateAppOps
}
