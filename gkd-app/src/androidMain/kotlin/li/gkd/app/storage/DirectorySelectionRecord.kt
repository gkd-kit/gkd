package li.gkd.app.storage

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
    InternalMarkerExists,
    ExternalAvailable,
    ExternalNullFallback,
}

@Serializable
enum class StorageLocation {
    Internal,
    External,
}
