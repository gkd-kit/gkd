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
)
@Parcelize
data class SubsConfig(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") override val id: Long = 0,
    @ColumnInfo(name = "ctime") override val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") override val mtime: Long = System.currentTimeMillis(),

    /**
     * 0 - app
     * 1 - group
     * 2 - rule
     */
    @ColumnInfo(name = "type") val type: Int = 0,
    @ColumnInfo(name = "enable") val enable: Boolean = true,

    @ColumnInfo(name = "subs_item_id") val subsItemId: Long = -1,
    @ColumnInfo(name = "app_id") val appId: String = "",
    @ColumnInfo(name = "group_key") val groupKey: Int = -1,
    @ColumnInfo(name = "rule_key") val ruleKey: Int = -1,
) : BaseTable, Parcelable {

    companion object {
        const val AppType = 0
        const val GroupType = 1
        const val RuleType = 2
    }

    @Dao
    interface RoomDao : BaseDao<SubsConfig>
}
