package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "subs_config",
)
data class SubsConfig(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = buildUniqueTimeMillisId(),
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "enable") val enable: Boolean? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "app_id") val appId: String = "",
    @ColumnInfo(name = "group_key") val groupKey: Int = -1,
    @ColumnInfo(name = "exclude", defaultValue = "") val exclude: String = "",
) {

    @Suppress("ConstPropertyName")
    companion object {
        const val AppGroupType = 2
        const val GlobalGroupType = 3
    }

    @Dao
    interface SubsConfigDao {

        @Query("SELECT * FROM subs_config")
        suspend fun queryAll(): List<SubsConfig>

        @Update
        suspend fun update(vararg objects: SubsConfig): Int

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg users: SubsConfig): List<Long>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg users: SubsConfig): List<Long>

        @Delete
        suspend fun delete(vararg users: SubsConfig): Int

        @Transaction
        suspend fun insertAndDelete(newList: List<SubsConfig>, deleteList: List<SubsConfig>) {
            insert(*newList.toTypedArray())
            delete(*deleteList.toTypedArray())
        }

        @Query("DELETE FROM subs_config WHERE subs_id=:subsItemId")
        suspend fun delete(subsItemId: Long): Int

        @Query("DELETE FROM subs_config WHERE subs_id IN (:subsIds)")
        suspend fun deleteBySubsId(vararg subsIds: Long): Int

        @Query("DELETE FROM subs_config WHERE subs_id=:subsItemId AND app_id=:appId")
        suspend fun deleteAppConfig(subsItemId: Long, appId: String): Int

        @Query("DELETE FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key=:groupKey")
        suspend fun deleteAppGroupConfig(subsItemId: Long, appId: String, groupKey: Int): Int


        @Query("DELETE FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key IN (:keyList)")
        suspend fun batchDeleteAppGroupConfig(
            subsItemId: Long,
            appId: String,
            keyList: List<Int>
        ): Int

        @Query("DELETE FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId AND group_key=:groupKey")
        suspend fun deleteGlobalGroupConfig(subsItemId: Long, groupKey: Int): Int

        @Query("DELETE FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId AND group_key IN (:keyList)")
        suspend fun batchDeleteGlobalGroupConfig(subsItemId: Long, keyList: List<Int>): Int

        @Query("SELECT * FROM subs_config WHERE subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedList(): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId")
        fun querySubsGroupTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId")
        fun queryAppGroupTypeConfig(subsItemId: Long, appId: String): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key=:groupKey")
        fun queryAppGroupTypeConfig(
            subsItemId: Long, appId: String, groupKey: Int
        ): Flow<SubsConfig?>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId")
        fun queryGlobalGroupTypeConfig(subsItemId: Long): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id=:subsItemId AND group_key=:groupKey")
        fun queryGlobalGroupTypeConfig(subsItemId: Long, groupKey: Int): Flow<SubsConfig?>

        @Query("SELECT * FROM subs_config WHERE type=${AppGroupType} AND app_id=:appId AND subs_id IN (:subsItemIds)")
        fun queryAppConfig(subsItemIds: List<Long>, appId: String): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id IN (:subsItemIds)")
        fun queryGlobalConfig(subsItemIds: List<Long>): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE type=${GlobalGroupType} AND subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedGlobalConfig(): Flow<List<SubsConfig>>

        @Query("SELECT * FROM subs_config WHERE subs_id IN (:subsItemIds) ")
        suspend fun querySubsItemConfig(subsItemIds: List<Long>): List<SubsConfig>

        @Query("UPDATE subs_config SET enable = null WHERE type=${AppGroupType} AND subs_id=:subsItemId AND app_id=:appId AND group_key=:groupKey AND enable IS NOT NULL")
        suspend fun resetAppGroupTypeEnable(subsItemId: Long, appId: String, groupKey: Int): Int

    }

}
