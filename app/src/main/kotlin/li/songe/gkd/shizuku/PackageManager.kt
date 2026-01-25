package li.songe.gkd.shizuku

import android.Manifest
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.permission.Manifest_permission_GET_APP_OPS_STATS
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.util.AndroidTarget

@Suppress("unused")
class SafePackageManager(private val value: IPackageManager) {
    companion object {

        fun newBinder() = getShizukuService("package")?.let {
            SafePackageManager(IPackageManager.Stub.asInterface(it))
        }

        private var canUseGetInstalledApps = true
    }

    val isSafeMode get() = safeInvokeShizuku { value.isSafeMode }

    fun getInstalledPackages(
        flags: Int,
        userId: Int = currentUserId,
    ): List<PackageInfo> = safeInvokeShizuku {
        if (AndroidTarget.TIRAMISU) {
            value.getInstalledPackages(flags.toLong(), userId).list
        } else {
            value.getInstalledPackages(flags, userId).list
        }
    } ?: emptyList()

    fun getPackageInfo(
        packageName: String,
        flags: Int,
        userId: Int,
    ): PackageInfo? = safeInvokeShizuku {
        if (AndroidTarget.TIRAMISU) {
            value.getPackageInfo(packageName, flags.toLong(), userId)
        } else {
            value.getPackageInfo(packageName, flags, userId)
        }
    }

    fun getApplicationEnabledSetting(
        packageName: String,
        userId: Int,
    ): Int = safeInvokeShizuku {
        value.getApplicationEnabledSetting(packageName, userId)
    } ?: 0

    private fun grantRuntimePermission(
        packageName: String,
        permissionName: String,
        userId: Int = currentUserId,
    ) = safeInvokeShizuku {
        value.grantRuntimePermission(
            packageName,
            permissionName,
            userId
        )
    }

    private fun grantSelfPermission(name: String, skipCheck: Boolean = false) {
        if (!skipCheck) {
            if (app.checkGrantedPermission(name)) return
        }
        grantRuntimePermission(
            packageName = META.appId,
            permissionName = name,
        )
    }

    fun checkUidPermission(permName: String, uid: Int): Int? = safeInvokeShizuku {
        value.checkUidPermission(
            permName,
            uid
        )
    }

    fun allowAllSelfPermission() {
        if (canUseGetInstalledApps && !canQueryPkgState.value) {
            try {
                grantSelfPermission("com.android.permission.GET_INSTALLED_APPS", skipCheck = true)
            } catch (_: IllegalArgumentException) {
                canUseGetInstalledApps = false
            }
        }
        grantSelfPermission(Manifest_permission_GET_APP_OPS_STATS)
        grantSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        if (AndroidTarget.TIRAMISU) {
            grantSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
