package li.songe.gkd.util

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 备注: 添加字段一定要在末尾添加,不能中间插入字段,否则之后值将会错乱
 */
@Parcelize
data class AppSettings(
    var ctime: Long = System.currentTimeMillis(),
    var mtime: Long = System.currentTimeMillis(),
    var enableService: Boolean = true,
    var excludeFromRecents: Boolean = true,
    var notificationVisible: Boolean = true,
    var enableDebugServer: Boolean = false,
    var httpServerPort: Int = 8888,
    var enableConsoleLogOut: Boolean = true,
) : Parcelable {
    fun commit(block: AppSettings.() -> Unit) {
        val backup = copy()
        block.invoke(this)
        if (this != backup) {
            mtime = System.currentTimeMillis()
            Storage.kv.encode(saveKey, this)
        }
    }
    companion object {
        const val saveKey = "settings-v1"
    }
}