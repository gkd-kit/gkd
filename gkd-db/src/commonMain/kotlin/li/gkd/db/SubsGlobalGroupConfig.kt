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
    tableName = "subs_global_group_config",
    primaryKeys = ["subs_id", "group_key"],
    foreignKeys = [ForeignKey(
        entity = SubsItem::class,
        parentColumns = ["id"],
        childColumns = ["subs_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SubsGlobalGroupConfig(
    @ColumnInfo(name = "subs_id") override val subsId: Long,
    @ColumnInfo(name = "group_key") override val groupKey: Int,
    @ColumnInfo(name = "enable") override val enable: Boolean? = null,
    @ColumnInfo(name = "exclude", defaultValue = "") override val exclude: String = "",
) : SubsGroupConfig {
    @Dao
    interface SubsGlobalGroupConfigDao {
        @Query("SELECT * FROM subs_global_group_config")
        suspend fun queryAll(): List<SubsGlobalGroupConfig>

        @Query("SELECT * FROM subs_global_group_config WHERE subs_id=:subsId")
        fun queryBySubsId(subsId: Long): Flow<List<SubsGlobalGroupConfig>>

        @Query("SELECT * FROM subs_global_group_config WHERE subs_id=:subsId AND group_key=:groupKey")
        fun queryConfig(subsId: Long, groupKey: Int): Flow<SubsGlobalGroupConfig?>

        @Query("SELECT * FROM subs_global_group_config WHERE subs_id=:subsId AND group_key=:groupKey")
        suspend fun getConfig(subsId: Long, groupKey: Int): SubsGlobalGroupConfig?

        @Query("SELECT * FROM subs_global_group_config WHERE subs_id IN (:subsIds)")
        suspend fun queryBySubsIds(subsIds: List<Long>): List<SubsGlobalGroupConfig>

        @Query("SELECT * FROM subs_global_group_config WHERE subs_id IN (SELECT id FROM subs_item WHERE enable = 1)")
        fun queryUsedList(): Flow<List<SubsGlobalGroupConfig>>

        @Upsert
        suspend fun upsert(vararg objects: SubsGlobalGroupConfig)

        @Update
        suspend fun update(vararg objects: SubsGlobalGroupConfig): Int

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg objects: SubsGlobalGroupConfig): List<Long>

        @Delete
        suspend fun delete(vararg objects: SubsGlobalGroupConfig): Int

        @Query("DELETE FROM subs_global_group_config WHERE subs_id=:subsId AND group_key IN (:keys)")
        suspend fun deleteGroups(subsId: Long, keys: List<Int>): Int
    }
}
