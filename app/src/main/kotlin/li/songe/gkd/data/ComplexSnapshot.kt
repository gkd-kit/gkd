package li.songe.gkd.data

import kotlinx.serialization.Serializable
import li.songe.gkd.app

@Serializable
data class ComplexSnapshot(
    override val id: Long,

    override val appId: String?,
    override val activityId: String?,

    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,

    val appInfo: AppInfo? = appId?.let { app.packageManager.getPackageInfo(appId, 0)?.toAppInfo() },
    val gkdAppInfo: AppInfo? = selfAppInfo,
    val device: DeviceInfo = DeviceInfo.instance,

    @Deprecated("use appInfo")
    override val appName: String? = appInfo?.name,
    @Deprecated("use appInfo")
    override val appVersionCode: Long? = appInfo?.versionCode,
    @Deprecated("use appInfo")
    override val appVersionName: String? = appInfo?.versionName,

    val nodes: List<NodeInfo>,
) : BaseSnapshot

fun ComplexSnapshot.toSnapshot(): Snapshot {
    return Snapshot(
        id = id,

        appId = appId,
        activityId = activityId,

        screenHeight = screenHeight,
        screenWidth = screenWidth,
        isLandscape = isLandscape,

        appName = appInfo?.name,
        appVersionCode = appInfo?.versionCode,
        appVersionName = appInfo?.versionName,
    )
}


