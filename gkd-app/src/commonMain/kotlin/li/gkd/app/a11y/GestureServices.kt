package li.gkd.app.a11y

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import li.gkd.app.platform.PlatformResult

@Serializable
data class GestureService(
    val id: String,
    val appId: String,
    val name: String,
    val appName: String,
    val isGkd: Boolean = false,
    val description: String = "",
)

expect fun observeEnabledGestureServices(): Flow<List<GestureService>>

expect fun observeGestureServiceWritePermission(): Flow<Boolean>

/** Success means the selected components were removed from the enabled services. */
expect suspend fun closeGestureServices(ids: Set<String>): PlatformResult<Unit>
