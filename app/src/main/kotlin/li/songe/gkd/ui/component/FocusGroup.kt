package li.songe.gkd.ui.component

import androidx.compose.runtime.Immutable

@Immutable
data class FocusGroup(
    val subsId: Long,
    val appId: String?,
    val groupKey: Int,
)

@Immutable
data class CheckedGroup(
    val subsId: Long,
    val groupType: Int,
    val groupKey: Int,
)
