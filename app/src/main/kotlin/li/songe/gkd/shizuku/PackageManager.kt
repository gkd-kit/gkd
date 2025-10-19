package li.songe.gkd.shizuku

import android.Manifest
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import li.songe.gkd.META
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.checkExistClass

class SafePackageManager(private val value: IPackageManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.content.pm.IPackageManager")

        fun newBinder() = getStubService(
            "package",
            isAvailable
        )?.let {
            SafePackageManager(IPackageManager.Stub.asInterface(it))
        }
    }

    val isSafeMode get() = safeInvokeMethod { value.isSafeMode }

    fun getInstalledPackages(
        flags: Int,
        userId: Int = currentUserId,
    ): List<PackageInfo> = safeInvokeMethod {
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
    ): PackageInfo? = safeInvokeMethod {
        if (AndroidTarget.TIRAMISU) {
            value.getPackageInfo(packageName, flags.toLong(), userId)
        } else {
            value.getPackageInfo(packageName, flags, userId)
        }
    }

    fun getApplicationEnabledSetting(
        packageName: String,
        userId: Int,
    ): Int = safeInvokeMethod {
        value.getApplicationEnabledSetting(packageName, userId)
    } ?: 0

    fun grantRuntimePermission(
        packageName: String,
        permissionName: String,
        userId: Int = currentUserId,
    ) = safeInvokeMethod {
        value.grantRuntimePermission(
            packageName,
            permissionName,
            userId
        )
    }

    private fun grantSelfPermission(permissionName: String) = grantRuntimePermission(
        packageName = META.appId,
        permissionName = permissionName,
    )

    fun allowAllSelfPermission() {
        grantSelfPermission("com.android.permission.GET_INSTALLED_APPS")
        grantSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        if (AndroidTarget.TIRAMISU) {
            grantSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
