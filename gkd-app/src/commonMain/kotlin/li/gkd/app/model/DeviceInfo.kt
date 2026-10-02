package li.gkd.app.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val device: String,
    val model: String,
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val release: String,
)
