package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "subs_app_config",
    primaryKeys = ["subs_id", "app_id"],
    foreignKeys = [ForeignKey(
        entity = SubsItem::class,
        parentColumns = ["id"],
        childColumns = ["subs_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SubsAppConfig(
    @ColumnInfo(name = "enable") val enable: Boolean,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String,
) {
    @Dao
    interface SubsAppConfigDao {
        @Query("SELECT * FROM subs_app_config")
        suspend fun queryAll(): List<SubsAppConfig>

        @Update
        suspend fun update(vararg objects: SubsAppConfig): Int

        @Upsert
        suspend fun upsert(vararg users: SubsAppConfig)

        @Query("SELECT * FROM subs_app_config WHERE subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedList(): Flow<List<SubsAppConfig>>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg objects: SubsAppConfig): List<Long>

        @Delete
        suspend fun delete(vararg objects: SubsAppConfig): Int

        @Query("SELECT * FROM subs_app_config WHERE subs_id IN (:subsItemIds)")
        suspend fun querySubsItemConfig(subsItemIds: List<Long>): List<SubsAppConfig>
    }
}
