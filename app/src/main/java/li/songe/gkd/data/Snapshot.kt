package li.songe.gkd.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Update
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import li.songe.gkd.accessibility.GkdAbService
import li.songe.gkd.db.IgnoreConverters
import li.songe.gkd.utils.Ext

@TypeConverters(IgnoreConverters::class)
@Entity(
    tableName = "snapshot",
)
@Serializable
data class Snapshot(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    @ColumnInfo(name = "app_name") val appName: String? = Ext.getAppName(appId),
    @ColumnInfo(name = "app_version_code") val appVersionCode: Int? = appId?.let {
        AppUtils.getAppVersionCode(
            appId
        )
    },
    @ColumnInfo(name = "app_version_name") val appVersionName: String? = appId?.let {
        AppUtils.getAppVersionName(
            appId
        )
    },

    @ColumnInfo(name = "screen_height") val screenHeight: Int = ScreenUtils.getScreenHeight(),
    @ColumnInfo(name = "screen_width") val screenWidth: Int = ScreenUtils.getScreenWidth(),
    @ColumnInfo(name = "is_landscape") val isLandscape: Boolean = ScreenUtils.isLandscape(),

    @ColumnInfo(name = "device") val device: String = DeviceInfo.instance.device,
    @ColumnInfo(name = "model") val model: String = DeviceInfo.instance.model,
    @ColumnInfo(name = "manufacturer") val manufacturer: String = DeviceInfo.instance.manufacturer,
    @ColumnInfo(name = "brand") val brand: String = DeviceInfo.instance.brand,
    @ColumnInfo(name = "sdk_int") val sdkInt: Int = DeviceInfo.instance.sdkInt,
    @ColumnInfo(name = "release") val release: String = DeviceInfo.instance.release,

    @ColumnInfo(name = "_1") val nodes: List<NodeInfo> = emptyList(),
) {
    companion object {
        fun current(includeNode: Boolean = true): Snapshot {
            val currentAbNode = GkdAbService.currentAbNode
            val appId = currentAbNode?.packageName?.toString()
            val currentActivityId = GkdAbService.currentActivityId
            return Snapshot(
                appId = appId,
                activityId = currentActivityId,
                nodes = if (includeNode) NodeInfo.info2nodeList(currentAbNode) else emptyList()
            )
        }
    }

    @Dao
    @TypeConverters(IgnoreConverters::class)
    interface SnapshotDao {
        @Update
        suspend fun update(vararg objects: Snapshot): Int

        @Insert
        suspend fun insert(vararg users: Snapshot): List<Long>

        @Delete
        suspend fun delete(vararg users: Snapshot): Int

        @Query("SELECT * FROM snapshot")
        fun query(): Flow<List<Snapshot>>
    }
}
