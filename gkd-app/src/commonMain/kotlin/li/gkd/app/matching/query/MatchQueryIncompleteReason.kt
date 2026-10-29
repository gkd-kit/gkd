package li.gkd.app.matching.query

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchQueryIncompleteReason(val wireValue: String) {
    @SerialName("query_root_unavailable") RootUnavailable("query_root_unavailable"),
    @SerialName("query_child_unavailable") ChildUnavailable("query_child_unavailable"),
    @SerialName("query_child_limit") ChildLimit("query_child_limit"),
    @SerialName("query_descendant_limit") DescendantLimit("query_descendant_limit"),
}
