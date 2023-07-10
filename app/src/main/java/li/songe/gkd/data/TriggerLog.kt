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
import kotlinx.parcelize.Parcelize
import java.nio.channels.Selector

@Entity(
    tableName = "trigger_log",
)
@Parcelize
data class TriggerLog(
    /**
     * 此 id 与某个 snapshot id 一致, 表示 one to one
     */
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,
    /**
     * 订阅文件 id
     */
    @ColumnInfo(name = "subs_id") val subsId: Long,
    /**
     * 触发的组 id
     */
    @ColumnInfo(name = "group_key") val groupKey: Int,

    /**
     * 触发的选择器
     */
    @ColumnInfo(name = "match") val match: String,

    ) : Parcelable {
    @Dao
    interface TriggerLogDao {

        @Update
        suspend fun update(vararg objects: TriggerLog): Int

        @Insert
        suspend fun insert(vararg users: TriggerLog): List<Long>

        @Delete
        suspend fun delete(vararg users: TriggerLog): Int

        @Query("SELECT * FROM trigger_log")
        suspend fun query(): List<TriggerLog>
    }
}
