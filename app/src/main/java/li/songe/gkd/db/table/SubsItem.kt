package li.songe.gkd.db.table

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import li.songe.gkd.db.BaseDao
import li.songe.gkd.db.BaseTable

@Entity(
    tableName = "subs_item",
    indices = [Index(value = ["update_url"], unique = true)]
)
@Parcelize
data class SubsItem(
    /**
     * 当主键是0时,autoGenerate将覆盖此字段,插入数据库后 需要用返回值手动更新此字段
     */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") override val id: Long = 0,
    @ColumnInfo(name = "ctime") override val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") override val mtime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "enable") val enable: Boolean = true,

    /**
     * 订阅文件 name 属性
     */
    @ColumnInfo(name = "name") val name: String = "",

    /**
     * 订阅文件下载地址,也是更新链接
     */
    @ColumnInfo(name = "update_url") val updateUrl: String = "",

    /**
     * 订阅文件下载地址,也是更新链接
     */
    @ColumnInfo(name = "version") val version: Int = 0,

    /**
     * 订阅文件下载后存放的路径
     */
    @ColumnInfo(name = "file_path") val filePath: String = "",

    /**
     * 顺序
     */
    @ColumnInfo(name = "index") val index: Int = 0,


    ) : Parcelable, BaseTable {
    @Dao
    interface RoomDao : BaseDao<SubsItem>
}
