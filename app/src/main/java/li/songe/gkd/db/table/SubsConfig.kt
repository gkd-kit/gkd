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
    tableName = "subs_config",
//    indices = [Index(value = ["url"], unique = true)]
)
@Parcelize
data class SubsConfig(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") override var id: Long = 0,
    @ColumnInfo(name = "ctime") override var ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") override var mtime: Long = System.currentTimeMillis(),

    /**
     * 0 - app
     * 1 - group
     * 2 - rule
     */
    @ColumnInfo(name = "type") var type: Int = 0,
    @ColumnInfo(name = "enable") var enable: Boolean = true,

    @ColumnInfo(name = "subs_item_id") var subsItemId: Long = -1,
    @ColumnInfo(name = "app_id") var appId: String = "",
    @ColumnInfo(name = "group_key") var groupKey: Int = -1,
    @ColumnInfo(name = "rule_key") var ruleKey: Int = -1,
) : BaseTable, Parcelable {

    companion object {
        const val AppType = 0
        const val GroupType = 1
        const val RuleType = 2
    }

    @Dao
    interface RoomDao : BaseDao<SubsConfig>
}
