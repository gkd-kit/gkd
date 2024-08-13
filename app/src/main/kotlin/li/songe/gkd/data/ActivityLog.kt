package li.songe.gkd.data

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import li.songe.gkd.util.format

@Entity(
    tableName = "activity_log",
)
data class ActivityLog(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
) {
    val showActivityId by lazy {
        if (activityId != null) {
            if (activityId.startsWith(
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
    val date by lazy { id.format("MM-dd HH:mm:ss SSS") }

    @Dao
    interface ActivityLogDao {
        @Insert
        suspend fun insert(vararg objects: ActivityLog): List<Long>

        @Query("DELETE FROM activity_log")
        suspend fun deleteAll()

        @Query("SELECT * FROM activity_log WHERE activity_id IS NOT NULL ORDER BY id DESC ")
        fun pagingSource(): PagingSource<Int, ActivityLog>

        @Query("SELECT COUNT(*) FROM activity_log WHERE activity_id IS NOT NULL")
        fun count(): Flow<Int>

        @Query(
            """
            DELETE FROM activity_log
            WHERE (
                    SELECT COUNT(*)
                    FROM activity_log
                ) > 1000
                AND id <= (
                    SELECT id
                    FROM activity_log
                    ORDER BY id DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int
    }
}
