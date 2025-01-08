package li.songe.gkd.shizuku

import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.util.storeFlow
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.typeOf


private var packageFlagsParamsLongType: Boolean? = null
private fun IPackageManager.compatGetInstalledPackages(
    flags: Long,
    userId: Int
): List<PackageInfo> {
    if (packageFlagsParamsLongType == null) {
        val method = this::class.declaredFunctions.find { it.name == "getInstalledPackages" }!!
        packageFlagsParamsLongType = method.parameters[1].type == typeOf<Long>()
    }
    return if (packageFlagsParamsLongType == true) {
        getInstalledPackages(flags, userId).list
    } else {
        getInstalledPackages(flags.toInt(), userId).list
    }
}

interface SafePackageManager {
    fun compatGetInstalledPackages(flags: Long, userId: Int): List<PackageInfo>
    fun getAllIntentFilters(packageName: String): List<IntentFilter>
}

fun newPackageManager(): SafePackageManager? {
    val service = SystemServiceHelper.getSystemService("package")
    if (service == null) {
        LogUtils.d("shizuku 无法获取 package")
        return null
    }
    val manager = service.let(::ShizukuBinderWrapper).let(IPackageManager.Stub::asInterface)
    return object : SafePackageManager {
        override fun compatGetInstalledPackages(flags: Long, userId: Int) =
            manager.compatGetInstalledPackages(flags, userId)

        override fun getAllIntentFilters(packageName: String) =
            manager.getAllIntentFilters(packageName).list
    }
}

val shizukuWorkProfileUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, storeFlow) { shizukuOk, store ->
        shizukuOk && store.enableShizukuWorkProfile
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val packageManagerFlow by lazy<StateFlow<SafePackageManager?>> {
    val stateFlow = MutableStateFlow<SafePackageManager?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuWorkProfileUsedFlow.collect {
            stateFlow.value = if (it) newPackageManager() else null
        }
    }
    stateFlow
}
