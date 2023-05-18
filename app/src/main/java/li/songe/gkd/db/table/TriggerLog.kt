package li.songe.gkd.db.table

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import li.songe.gkd.db.BaseDao
import li.songe.gkd.db.BaseTable

@Entity(
    tableName = "trigger_log",
)
@Parcelize
data class TriggerLog(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") override val id: Long = 0,
    @ColumnInfo(name = "ctime") override val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") override val mtime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String? = null,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    @ColumnInfo(name = "selector") val selector: String = ""
) : Parcelable, BaseTable {
    @Dao
    interface RoomDao : BaseDao<TriggerLog>
}
