package li.gkd.app.model

import kotlinx.serialization.Serializable

@Serializable
data class AttrInfo(
    val id: String?,
    val vid: String?,
    val name: String?,
    val text: String?,
    val desc: String?,

    val clickable: Boolean,
    val focusable: Boolean,
    val checkable: Boolean,
    val checked: Boolean?,
    val editable: Boolean,
    val longClickable: Boolean,
    val visibleToUser: Boolean,

    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,

    val width: Int,
    val height: Int,

    val childCount: Int,

    val index: Int,
    val depth: Int,
)
