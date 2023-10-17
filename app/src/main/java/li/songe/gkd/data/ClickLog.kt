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
    tableName = "click_log",
)
@Parcelize
data class ClickLog(
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
        suspend fun update(vararg objects: ClickLog): Int

        @Insert
        suspend fun insert(vararg objects: ClickLog): List<Long>

        @Delete
        suspend fun delete(vararg objects: ClickLog): Int

        @Query("DELETE FROM click_log")
        suspend fun deleteAll()

        @Query("SELECT * FROM click_log ORDER BY id DESC LIMIT 1000")
        fun query(): Flow<List<ClickLog>>

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
    }
}
