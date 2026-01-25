package li.songe.gkd.shizuku

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommandResult(
    val code: Int?,
    val result: String,
    val error: String?
) : Parcelable {
    val ok: Boolean
        get() = code == 0
}
