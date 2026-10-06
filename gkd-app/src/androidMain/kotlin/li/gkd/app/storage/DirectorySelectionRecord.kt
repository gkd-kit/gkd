package li.gkd.app.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DirectorySelectionRecord(
    val markerBefore: Boolean,
    val externalResult: String?,
    val reason: StorageSelectionReason,
    val markerCreated: Boolean?,
    val selectedPath: String,
    val storageLocation: StorageLocation,
)

@Serializable
enum class StorageSelectionReason {
    @SerialName("internal_marker_exists")
    InternalMarkerExists,
    @SerialName("external_available")
    ExternalAvailable,
    @SerialName("external_null_fallback")
    ExternalNullFallback,
}

@Serializable
enum class StorageLocation {
    @SerialName("internal")
    Internal,
    @SerialName("external")
    External,
}
