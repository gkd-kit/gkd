package li.gkd.app.priv

import android.content.pm.PackageManager
import li.gkd.app.permission.AndroidPermissions
import li.gkd.app.util.AndroidTarget
import priv.kit.core.Privilege

fun hasAppOpsPermission(): Boolean = Privilege.checkServerPermission(
    if (AndroidTarget.P) AndroidPermissions.MANAGE_APP_OPS_MODES
    else AndroidPermissions.UPDATE_APP_OPS_STATS
) == PackageManager.PERMISSION_GRANTED

fun queryPrivilegeCapabilities(): PrivilegeCapabilities = PrivilegeCapabilities(
    grantRuntimePermissions = Privilege.checkServerPermission(AndroidPermissions.GRANT_RUNTIME_PERMISSIONS) == PackageManager.PERMISSION_GRANTED,
    injectEvents = Privilege.checkServerPermission(AndroidPermissions.INJECT_EVENTS) == PackageManager.PERMISSION_GRANTED,
    writeSecureSettings = Privilege.checkServerPermission(AndroidPermissions.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
    updateAppOps = hasAppOpsPermission(),
)
