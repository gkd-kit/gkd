package li.songe.gkd.data

import kotlinx.serialization.Serializable

@Serializable
data class GithubPoliciesAsset(
    val id: Int,
    val href: String,
)
