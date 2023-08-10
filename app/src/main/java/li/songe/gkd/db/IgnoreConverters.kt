package li.songe.gkd.db

import androidx.room.TypeConverter
import li.songe.gkd.data.NodeInfo

object IgnoreConverters {
    @TypeConverter
    @JvmStatic
    fun listToCol(list: List<NodeInfo>): String = ""

    @TypeConverter
    @JvmStatic
    fun colToList(value: String): List<NodeInfo> = emptyList()
}