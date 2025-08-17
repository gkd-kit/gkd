package li.songe.gkd.shizuku

import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.typeOf


private var packageFcType: Boolean? = null
private fun IPackageManager.compatGetInstalledPackages(
    flags: Long,
    userId: Int
): List<PackageInfo> {
    if (packageFcType == null) {
        packageFcType = this::class.declaredMemberFunctions.find {
            it.name == "getInstalledPackages"
        }!!.parameters[1].type == typeOf<Long>()
    }
    return if (packageFcType == true) {
        getInstalledPackages(flags, userId)
    } else {
        getInstalledPackages(flags.toInt(), userId)
    }.list
}

class SafePackageManager(private val value: IPackageManager) {
    fun compatGetInstalledPackages(flags: Long, userId: Int): List<PackageInfo> {
        return value.compatGetInstalledPackages(flags, userId)
    }

    fun getAllIntentFilters(packageName: String): List<IntentFilter> {
        return value.getAllIntentFilters(packageName).list
    }
}
