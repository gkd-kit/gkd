package li.gkd.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** Local cumulative history, independent of log retention and subscription deletion. */
@Entity(
    tableName = "rule_trigger_stats",
    primaryKeys = ["subs_id", "group_type", "group_key", "app_id"],
    indices = [Index(value = ["app_id"])],
)
data class RuleTriggerStats(
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "group_type") val groupType: Int,
    @ColumnInfo(name = "group_key") val groupKey: Int,
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "trigger_count") val triggerCount: Long,
    @ColumnInfo(name = "last_trigger_time") val lastTriggerTime: Long,
)

@Dao
interface RuleTriggerStatsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: RuleTriggerStats)

    @Query("""
        UPDATE rule_trigger_stats SET trigger_count = trigger_count + 1,
            last_trigger_time = MAX(last_trigger_time, :time)
        WHERE subs_id = :subsId AND group_type = :groupType AND group_key = :groupKey AND app_id = :appId
    """)
    suspend fun increment(subsId: Long, groupType: Int, groupKey: Int, appId: String, time: Long)

    @Query("""
        SELECT * FROM rule_trigger_stats
        WHERE (:subsId IS NULL OR subs_id = :subsId)
            AND (:appId IS NULL OR app_id = :appId)
            AND (:groupType IS NULL OR group_type = :groupType)
            AND (:groupKey IS NULL OR group_key = :groupKey)
    """)
    fun observe(subsId: Long? = null, appId: String? = null, groupType: Int? = null, groupKey: Int? = null): Flow<List<RuleTriggerStats>>
}
