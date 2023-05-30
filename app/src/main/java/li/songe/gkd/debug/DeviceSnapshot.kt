package li.songe.gkd.debug

import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceSnapshot(
    @SerialName("device")
    val device: String = Build.DEVICE,
    @SerialName("model")
    val model: String = Build.MODEL,
    @SerialName("manufacturer")
    val manufacturer: String = Build.MANUFACTURER,
    @SerialName("brand")
    val brand: String = Build.BRAND,
    @SerialName("sdkInt")
    val sdkInt: Int = Build.VERSION.SDK_INT,
    @SerialName("release")
    val release: String = Build.VERSION.RELEASE,
){
    companion object{
        val instance by lazy { DeviceSnapshot() }
    }
}
