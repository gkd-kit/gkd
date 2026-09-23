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

@Entity(
    tableName = "subs_app_group_config",
    primaryKeys = ["subs_id", "app_id", "group_key"],
    foreignKeys = [ForeignKey(
        entity = SubsItem::class,
        parentColumns = ["id"],
        childColumns = ["subs_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SubsAppGroupConfig(
    @ColumnInfo(name = "subs_id") override val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "group_key") override val groupKey: Int,
    @ColumnInfo(name = "enable") override val enable: Boolean? = null,
    @ColumnInfo(name = "exclude", defaultValue = "") override val exclude: String = "",
) : SubsGroupConfig {
    @Dao
    interface SubsAppGroupConfigDao {
        @Query("SELECT * FROM subs_app_group_config")
        suspend fun queryAll(): List<SubsAppGroupConfig>

        @Query("SELECT * FROM subs_app_group_config WHERE subs_id=:subsId")
        fun queryBySubsId(subsId: Long): Flow<List<SubsAppGroupConfig>>

        @Query("SELECT * FROM subs_app_group_config WHERE subs_id=:subsId AND app_id=:appId AND group_key=:groupKey")
        fun queryConfig(subsId: Long, appId: String, groupKey: Int): Flow<SubsAppGroupConfig?>

        @Query("SELECT * FROM subs_app_group_config WHERE subs_id=:subsId AND app_id=:appId AND group_key=:groupKey")
        suspend fun getConfig(subsId: Long, appId: String, groupKey: Int): SubsAppGroupConfig?

        @Query("SELECT * FROM subs_app_group_config WHERE subs_id IN (:subsIds)")
        suspend fun queryBySubsIds(subsIds: List<Long>): List<SubsAppGroupConfig>

        @Query("SELECT * FROM subs_app_group_config WHERE subs_id IN (SELECT id FROM subs_item WHERE enable = 1)")
        fun queryUsedList(): Flow<List<SubsAppGroupConfig>>

        @Upsert
        suspend fun upsert(vararg objects: SubsAppGroupConfig)

        @Update
        suspend fun update(vararg objects: SubsAppGroupConfig): Int

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg objects: SubsAppGroupConfig): List<Long>

        @Delete
        suspend fun delete(vararg objects: SubsAppGroupConfig): Int

        @Query("SELECT * FROM subs_app_group_config WHERE subs_id=:subsId AND app_id=:appId")
        fun queryByAppId(subsId: Long, appId: String): Flow<List<SubsAppGroupConfig>>

        @Query("SELECT * FROM subs_app_group_config WHERE app_id=:appId AND subs_id IN (:subsIds)")
        fun queryAppConfig(subsIds: List<Long>, appId: String): Flow<List<SubsAppGroupConfig>>

        @Query("DELETE FROM subs_app_group_config WHERE subs_id=:subsId AND app_id=:appId")
        suspend fun deleteAppConfig(subsId: Long, appId: String): Int

        @Query("DELETE FROM subs_app_group_config WHERE subs_id=:subsId AND app_id=:appId AND group_key IN (:keys)")
        suspend fun deleteGroups(subsId: Long, appId: String, keys: List<Int>): Int

    }
}
