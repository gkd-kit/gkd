package li.songe.gkd.data

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import li.songe.gkd.util.format
import li.songe.gkd.util.getShowActivityId

@Serializable
@Entity(
    tableName = "action_log",
)
data class ActionLog(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "subs_version", defaultValue = "0") val subsVersion: Int,
    @ColumnInfo(name = "group_key") val groupKey: Int,
    @ColumnInfo(name = "group_type", defaultValue = "2") val groupType: Int,
    @ColumnInfo(name = "rule_index") val ruleIndex: Int,
    @ColumnInfo(name = "rule_key") val ruleKey: Int? = null,
) {

    val showActivityId by lazy { getShowActivityId(appId, activityId) }

    val date by lazy { ctime.format("MM-dd HH:mm:ss SSS") }

    @DeleteTable.Entries(
        DeleteTable(tableName = "click_log")
    )
    class ActionLogSpec : AutoMigrationSpec


    @Dao
    interface ActionLogDao {

        @Update
        suspend fun update(vararg objects: ActionLog): Int

        @Insert
        suspend fun insert(vararg objects: ActionLog): List<Long>

        @Delete
        suspend fun delete(vararg objects: ActionLog): Int


        @Query("DELETE FROM action_log WHERE subs_id IN (:subsIds)")
        suspend fun deleteBySubsId(vararg subsIds: Long): Int

        @Query("DELETE FROM action_log")
        suspend fun deleteAll()

        @Query("DELETE FROM action_log WHERE subs_id=:subsId")
        suspend fun deleteSubsAll(subsId: Long)

        @Query("DELETE FROM action_log WHERE app_id=:appId")
        suspend fun deleteAppAll(appId: String)

        @Query("SELECT * FROM action_log ORDER BY id DESC LIMIT 1000")
        fun query(): Flow<List<ActionLog>>

        @Query("SELECT * FROM action_log ORDER BY id DESC ")
        fun pagingSource(): PagingSource<Int, ActionLog>

        @Query("SELECT * FROM action_log WHERE subs_id=:subsId ORDER BY id DESC ")
        fun pagingSubsSource(subsId: Long): PagingSource<Int, ActionLog>

        @Query("SELECT * FROM action_log WHERE app_id=:appId ORDER BY id DESC ")
        fun pagingAppSource(appId: String): PagingSource<Int, ActionLog>

        @Query("SELECT COUNT(*) FROM action_log")
        fun count(): Flow<Int>


        @Query("SELECT * FROM action_log ORDER BY id DESC LIMIT 1")
        fun queryLatest(): Flow<ActionLog?>

        @Query(
            """
            SELECT cl.* FROM action_log AS cl
            INNER JOIN (
                SELECT subs_id, group_type, group_key, MAX(id) AS max_id FROM action_log
                WHERE app_id = :appId AND subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)
                GROUP BY subs_id, group_type, group_key
            ) AS latest_log ON cl.subs_id = latest_log.subs_id 
            AND cl.group_type = latest_log.group_type
            AND cl.group_key = latest_log.group_key
            AND cl.id = latest_log.max_id
        """
        )
        fun queryLatestByAppId(appId: String): Flow<List<ActionLog>>


        @Query(
            """
            DELETE FROM action_log
            WHERE (
                    SELECT COUNT(*)
                    FROM action_log
                ) > 1000
                AND id <= (
                    SELECT id
                    FROM action_log
                    ORDER BY id DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int

        @Query("SELECT DISTINCT app_id FROM action_log ORDER BY id DESC")
        fun queryLatestUniqueAppIds(): Flow<List<String>>

        @Query("SELECT DISTINCT app_id FROM action_log WHERE subs_id=:subsItemId AND group_type=${SubsConfig.AppGroupType} ORDER BY id DESC")
        fun queryLatestUniqueAppIds(subsItemId: Long): Flow<List<String>>

        @Query("SELECT DISTINCT app_id FROM action_log WHERE subs_id=:subsItemId AND group_key=:globalGroupKey AND group_type=${SubsConfig.GlobalGroupType} ORDER BY id DESC")
        fun queryLatestUniqueAppIds(subsItemId: Long, globalGroupKey: Int): Flow<List<String>>
    }
}
