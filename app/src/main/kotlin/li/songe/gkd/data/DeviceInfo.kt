package li.songe.gkd.data

import android.os.Build
import kotlinx.serialization.Serializable
import li.songe.gkd.BuildConfig

@Serializable
data class DeviceInfo(
    val device: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val release: String,
    val gkdVersionCode: Int,
    val gkdVersionName: String
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
                gkdVersionCode = BuildConfig.VERSION_CODE,
                gkdVersionName = BuildConfig.VERSION_NAME,
            )
        }
    }
}
