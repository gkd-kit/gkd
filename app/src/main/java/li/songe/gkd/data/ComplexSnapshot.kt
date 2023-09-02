package li.songe.gkd.data

import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import li.songe.gkd.service.GkdAbService
import li.songe.gkd.service.activityIdFlow

@Serializable
data class ComplexSnapshot(
    override val id: Long,

    override val appId: String?,
    override val activityId: String?,
    override val appName: String?,
    override val appVersionCode: Int?,
    override val appVersionName: String?,

    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,

    val device: DeviceInfo,
    val nodes: List<NodeInfo>,
) : BaseSnapshot


fun createComplexSnapshot(): ComplexSnapshot {
    val currentAbNode = GkdAbService.currentAbNode
    val appId = currentAbNode?.packageName?.toString()
    val currentActivityId = activityIdFlow.value
    val appInfo = if (appId == null) null else AppUtils.getAppInfo(appId)

    return ComplexSnapshot(
        id = System.currentTimeMillis(),

        appId = appId,
        activityId = currentActivityId,
        appName = appInfo?.name,
        appVersionCode = appInfo?.versionCode,
        appVersionName = appInfo?.versionName,

        screenHeight = ScreenUtils.getScreenHeight(),
        screenWidth = ScreenUtils.getScreenWidth(),
        isLandscape = ScreenUtils.isLandscape(),
        device = DeviceInfo.instance,
        nodes = NodeInfo.info2nodeList(currentAbNode)
    )
}

fun ComplexSnapshot.toSnapshot(): Snapshot {
    return Snapshot(
        id = id,

        appId = appId,
        activityId = activityId,
        appName = appName,
        appVersionCode = appVersionCode,
        appVersionName = appVersionName,

        screenHeight = screenHeight,
        screenWidth = screenWidth,
        isLandscape = isLandscape,
    )
}


