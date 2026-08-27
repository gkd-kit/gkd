package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "app_config",
)
data class AppConfig(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = buildUniqueTimeMillisId(),
    @ColumnInfo(name = "enable") val enable: Boolean,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String,
) {
    @Dao
    interface AppConfigDao {
        @Query("SELECT * FROM app_config")
        suspend fun queryAll(): List<AppConfig>

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
