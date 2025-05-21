package li.songe.gkd.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "app_config",
)
@Parcelize
data class AppConfig(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "enable") val enable: Boolean,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String,
) : Parcelable {
    @Dao
    interface AppConfigDao {
        @Update
        suspend fun update(vararg objects: AppConfig): Int

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg users: AppConfig): List<Long>

        @Query("SELECT * FROM app_config WHERE subs_id=:subsId")
        fun queryAppTypeConfig(subsId: Long): Flow<List<AppConfig>>

        @Query("SELECT * FROM app_config WHERE app_id=:appId AND subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryAppUsedList(appId: String): Flow<List<AppConfig>>

        @Query("SELECT * FROM app_config WHERE subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedList(): Flow<List<AppConfig>>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg objects: AppConfig): List<Long>

        @Query("SELECT * FROM app_config WHERE subs_id IN (:subsItemIds)")
        suspend fun querySubsItemConfig(subsItemIds: List<Long>): List<AppConfig>
    }
}
