package li.songe.gkd.composition

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class InvokeMessage(
    @SerialName("name") val name: String?,
    @SerialName("method") val method: String,
) : Parcelable
