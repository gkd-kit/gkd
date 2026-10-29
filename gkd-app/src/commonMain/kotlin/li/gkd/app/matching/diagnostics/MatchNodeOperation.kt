package li.gkd.app.matching.diagnostics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchNodeOperation(val wireValue: String) {
    @SerialName("queryRoot") QueryRoot("queryRoot"),
    @SerialName("attr") Attr("attr"),
    @SerialName("invoke") Invoke("invoke"),
    @SerialName("name") Name("name"),
    @SerialName("count") Count("count"),
    @SerialName("child") Child("child"),
    @SerialName("parent") Parent("parent"),
    @SerialName("root") Root("root"),
    @SerialName("key") Key("key"),
    @SerialName("children") Children("children"),
    @SerialName("descendants") Descendants("descendants"),
    @SerialName("fast") Fast("fast"),
    @SerialName("childRelation") ChildRelation("childRelation"),
    @SerialName("ancestorRelation") AncestorRelation("ancestorRelation"),
    @SerialName("previousRelation") PreviousRelation("previousRelation"),
    @SerialName("followingRelation") FollowingRelation("followingRelation"),
    @SerialName("descendantRelation") DescendantRelation("descendantRelation"),
    @SerialName("iterator") Iterator("iterator"),
    @SerialName("hasNext") HasNext("hasNext"),
    @SerialName("next") Next("next"),
}
