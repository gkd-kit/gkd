package li.songe.gkd.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "trigger_log",
)
@Parcelize
data class TriggerLog(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "group_key") val groupKey: Int,
    @ColumnInfo(name = "rule_index") val ruleIndex: Int,
    @ColumnInfo(name = "rule_key") val ruleKey: Int? = null,
) : Parcelable {
    @Dao
    interface TriggerLogDao {

        @Update
        suspend fun update(vararg objects: TriggerLog): Int

        @Insert
        suspend fun insert(vararg objects: TriggerLog): List<Long>

        @Delete
        suspend fun delete(vararg objects: TriggerLog): Int

        @Query("SELECT * FROM trigger_log ORDER BY id DESC")
        fun query(): Flow<List<TriggerLog>>

        @Query("SELECT COUNT(*) FROM trigger_log")
        fun count(): Flow<Int>
    }
}
