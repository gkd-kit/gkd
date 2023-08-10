package li.songe.gkd.notif

import li.songe.gkd.util.SafeR

data class Notif(
    val id: Int,
    val icon: Int,
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
)


const val STATUS_NOTIF_ID = 100

val abNotif by lazy {
    Notif(
        id = STATUS_NOTIF_ID,
        icon = SafeR.ic_launcher,
        title = "搞快点",
        text = "无障碍正在运行",
        ongoing = true,
        autoCancel = false
    )
}
