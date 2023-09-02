package li.songe.gkd.data

import android.os.Build
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val device: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val release: String,
) {
    companion object {
        val instance by lazy {
            DeviceInfo(
                device = Build.DEVICE,
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE,
            )
        }
    }
}
