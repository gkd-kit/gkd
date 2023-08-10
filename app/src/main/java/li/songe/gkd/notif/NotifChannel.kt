package li.songe.gkd.notif

data class NotifChannel(
    val id: String,
    val name: String,
    val desc: String,
)

val defaultChannel by lazy {
    NotifChannel(
        id = "default", name = "搞快点", desc = "显示服务运行状态"
    )
}