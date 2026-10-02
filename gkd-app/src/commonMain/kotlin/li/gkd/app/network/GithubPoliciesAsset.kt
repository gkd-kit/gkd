package li.gkd.app.network

import kotlinx.serialization.Serializable


@Serializable
data class GithubPoliciesAsset(
    val id: Int,
    val href: String,
) {
    val shortHref: String
        get() = AppLinks.FileShort + id
}
