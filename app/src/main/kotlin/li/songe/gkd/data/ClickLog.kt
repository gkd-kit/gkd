package li.songe.gkd.data

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.util.format

@Entity(
    tableName = "click_log",
)
data class ClickLog(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "subs_version", defaultValue = "0") val subsVersion: Int,
    @ColumnInfo(name = "group_key") val groupKey: Int,
    @ColumnInfo(name = "group_type", defaultValue = "2") val groupType: Int,
    @ColumnInfo(name = "rule_index") val ruleIndex: Int,
    @ColumnInfo(name = "rule_key") val ruleKey: Int? = null,
) {

    val showActivityId by lazy {
        if (activityId != null) {
            if (appId != null && activityId.startsWith(
                    appId
                )
            ) {
                activityId.substring(appId.length)
            } else {
                activityId
            }
        } else {
            null
        }
    }

    val date by lazy { id.format("MM-dd HH:mm:ss") }


    @Dao
    interface TriggerLogDao {

        @Update
        suspend fun update(vararg objects: ClickLog): Int

        @Insert
        suspend fun insert(vararg objects: ClickLog): List<Long>

        @Delete
        suspend fun delete(vararg objects: ClickLog): Int


        @Query("DELETE FROM click_log WHERE subs_id=:subsItemId")
        suspend fun deleteBySubsId(subsItemId: Long): Int

        @Query("DELETE FROM click_log")
        suspend fun deleteAll()

        @Query("SELECT * FROM click_log ORDER BY id DESC LIMIT 1000")
        fun query(): Flow<List<ClickLog>>

        @Query("SELECT * FROM click_log ORDER BY id DESC ")
        fun pagingSource(): PagingSource<Int, ClickLog>

        @Query("SELECT COUNT(*) FROM click_log")
        fun count(): Flow<Int>


        @Query("SELECT * FROM click_log ORDER BY id DESC LIMIT 1")
        fun queryLatest(): Flow<ClickLog?>


        @Query(
            """
            DELETE FROM click_log
            WHERE (
                    SELECT COUNT(*)
                    FROM click_log
                ) > 1000
                AND id <= (
                    SELECT id
                    FROM click_log
                    ORDER BY id DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int

        @Query("SELECT DISTINCT app_id FROM click_log ORDER BY id DESC")
        fun queryLatestUniqueAppIds(): Flow<List<String>>

        @Query("SELECT DISTINCT app_id FROM click_log WHERE subs_id=:subsItemId AND group_type=${SubsConfig.AppGroupType} ORDER BY id DESC")
        fun queryLatestUniqueAppIds(subsItemId: Long): Flow<List<String>>

        @Query("SELECT DISTINCT app_id FROM click_log WHERE subs_id=:subsItemId AND group_key=:globalGroupKey AND group_type=${SubsConfig.GlobalGroupType} ORDER BY id DESC")
        fun queryLatestUniqueAppIds(subsItemId: Long, globalGroupKey: Int): Flow<List<String>>
    }
}
