package li.gkd.app.matching.diagnostics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchRecordedValueType {
    @SerialName("null") Null,
    @SerialName("string") String,
    @SerialName("boolean") Boolean,
    @SerialName("int") Int,
    @SerialName("long") Long,
    @SerialName("float") Float,
    @SerialName("double") Double,
    @SerialName("candidate") Candidate,
    @SerialName("reference") Reference,
    @SerialName("key") Key,
    @SerialName("node_identity") NodeIdentity,
}
