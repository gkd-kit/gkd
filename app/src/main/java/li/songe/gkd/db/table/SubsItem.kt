package li.songe.gkd.db.table

import android.os.Parcelable
import androidx.room.*
import kotlinx.parcelize.Parcelize
import li.songe.gkd.db.BaseDao
import li.songe.gkd.db.BaseTable

@Entity(
    tableName = "subs_item",
    indices = [Index(value = ["url"], unique = true)]
)
@Parcelize
data class SubsItem(
    /**
     * 当主键是0时,autoGenerate将覆盖此字段,插入数据库后 需要用返回值手动更新此字段
     */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") override var id: Long = 0,
    @ColumnInfo(name = "ctime") override var ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") override var mtime: Long = System.currentTimeMillis(),
    /**
     * 订阅文件下载地址,也是更新链接
     */
    @ColumnInfo(name = "url") var url: String,
    /**
     * 订阅文件下载后存放的路径
     */
    @ColumnInfo(name = "file_path") var filePath: String,
    /**
     * 用户自定义备注
     */
    @ColumnInfo(name = "comment") var comment: String = "",
    /**
     * 来自订阅文件的描述,不应该由用户write
     */
    @ColumnInfo(name = "description") var description: String = "",
    @ColumnInfo(name = "enable") var enable: Boolean = true,

    ) : Parcelable, BaseTable {
    @Dao
    interface RoomDao : BaseDao<SubsItem>
}
