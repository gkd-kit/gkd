package li.songe.gkd.data

import kotlinx.coroutines.flow.MutableStateFlow

data class UserInfo(
    val id: Int,
    val name: String,
)

val otherUserMapFlow = MutableStateFlow(emptyMap<Int, UserInfo>())
