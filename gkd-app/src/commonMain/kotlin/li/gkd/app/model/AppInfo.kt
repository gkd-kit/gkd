package li.gkd.app.model

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(
    val id: String,
    val name: String,
    val versionCode: Int,
    val versionName: String?,
    val isSystem: Boolean,
    val mtime: Long,
    val hidden: Boolean,
    val userId: Int,
) {
    // AppInfo 完全由系统获取，id、userId 和 mtime 已足以表达唯一性，无需比较其他字段。
    override fun equals(other: Any?): Boolean {
        if (other !is AppInfo) return false
        return id == other.id && mtime == other.mtime && userId == other.userId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mtime.hashCode()
        result = 31 * result + userId
        return result
    }
}

