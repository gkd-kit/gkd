package li.songe.gkd.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val id: Int,
    val name: String,
)

val otherUserMapFlow = MutableStateFlow(emptyMap<Int, UserInfo>())
