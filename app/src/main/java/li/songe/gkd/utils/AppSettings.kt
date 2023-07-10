package li.songe.gkd.utils

import android.os.Parcelable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.parcelize.Parcelize

/**
 * 备注: 添加字段一定要在末尾添加,不能中间插入字段,否则之后值将会错乱
 */
@Parcelize
data class AppSettings(
    var enableService: Boolean = true,
    var excludeFromRecents: Boolean = true,
    var notificationVisible: Boolean = true,
    var enableConsoleLogOut: Boolean = true,
    var enableCaptureSystemScreenshot: Boolean = true,
    var httpServerPort: Int = 8888,
) : Parcelable {
    fun commit(block: AppSettings.() -> Unit) {
        val backup = copy()
        block.invoke(this)
        if (this != backup) {
            Storage.kv.encode(saveKey, this)
        }
    }

    companion object {
        const val saveKey = "settings-v2"
    }
}

val appSettingsFlow by lazy { MutableStateFlow(AppSettings()) }
